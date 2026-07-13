// Copyright 2010-2018, Google Inc.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package org.mozc.android.inputmethod.japanese;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Helpers for edge-to-edge layout on Android 15+ (targetSdk 35+).
 */
public final class EdgeToEdgeUtil {
  private EdgeToEdgeUtil() {}

  /** Enables drawing behind system bars for an {@link Activity}. */
  public static void enable(Activity activity) {
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      return;
    }
    enable(activity.getWindow());
  }

  /** Enables drawing behind system bars for an IME or other {@link Window}. */
  public static void enable(Window window) {
    if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      return;
    }
    WindowCompat.setDecorFitsSystemWindows(window, false);
  }

  /**
   * Applies system bar insets as padding on {@code contentView}, which is typically the view
   * returned from {@link Activity#setContentView}.
   */
  public static void applySystemBarInsets(View contentView) {
    if (contentView == null) {
      return;
    }
    ViewCompat.setOnApplyWindowInsetsListener(contentView,
        (view, windowInsets) -> {
          Insets bars = windowInsets.getInsets(
              WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return WindowInsetsCompat.CONSUMED;
        });
    ViewCompat.requestApplyInsets(contentView);
  }

  /**
   * Applies bottom inset as margin on the IME keyboard container.
   *
   * <p>Insets are read from both the IME decor view and the input view because some devices do not
   * dispatch usable insets to the input view alone. When insets are still unavailable, a system
   * resource fallback is used so gesture navigation does not clip the bottom keyboard row.
   */
  public static void applyImeNavigationBarInsets(InputMethodService service, View inputView) {
    if (service == null || inputView == null) {
      return;
    }
    Context context = service.getApplicationContext();
    View decorView = service.getWindow().getWindow().getDecorView();

    ViewCompat.setOnApplyWindowInsetsListener(decorView,
        (view, windowInsets) -> {
          applyImeBottomInset(inputView, getImeBottomInset(context, windowInsets));
          return windowInsets;
        });
    ViewCompat.setOnApplyWindowInsetsListener(inputView,
        (view, windowInsets) -> {
          int bottomInset = getImeBottomInset(context, windowInsets);
          applyImeBottomInset(inputView, bottomInset);
          return windowInsets.inset(0, 0, 0, bottomInset);
        });

    refreshImeNavigationBarInsets(service, inputView);
  }

  /** Re-reads insets and reapplies the keyboard bottom offset. */
  public static void refreshImeNavigationBarInsets(InputMethodService service, View inputView) {
    if (service == null || inputView == null) {
      return;
    }
    Context context = service.getApplicationContext();
    View decorView = service.getWindow().getWindow().getDecorView();
    ViewCompat.requestApplyInsets(decorView);
    ViewCompat.requestApplyInsets(inputView);
    applyImeBottomInset(inputView, getNavigationBarBottomInset(context, decorView, inputView));
  }

  /** Returns the bottom inset for {@code view}, or 0 if unavailable. */
  public static int getNavigationBarBottomInset(View view) {
    return getNavigationBarBottomInset(view != null ? view.getContext() : null, view);
  }

  /** Returns the bottom inset using {@code context} and the best available inset source. */
  public static int getNavigationBarBottomInset(Context context, View... views) {
    int bottom = 0;
    if (views != null) {
      for (View view : views) {
        if (view == null) {
          continue;
        }
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(view);
        bottom = Math.max(bottom, getImeBottomInset(context, insets));
        if (bottom > 0) {
          break;
        }
      }
    }
    if (bottom == 0 && context != null) {
      bottom = getFallbackNavigationBarHeight(context);
    }
    return bottom;
  }

  private static int getImeBottomInset(Context context, WindowInsetsCompat windowInsets) {
    int bottom = 0;
    if (windowInsets != null) {
      bottom = Math.max(
          bottom, windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom);
      bottom = Math.max(
          bottom, windowInsets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom);
      bottom = Math.max(
          bottom, windowInsets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        bottom = Math.max(
            bottom,
            windowInsets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).bottom);
      }
    }
    if (bottom == 0 && context != null) {
      bottom = getFallbackNavigationBarHeight(context);
    }
    return bottom;
  }

  private static int getFallbackNavigationBarHeight(Context context) {
    Resources system = Resources.getSystem();
    int height = 0;
    height = Math.max(height, getSystemDimen(system, "navigation_bar_height"));
    height = Math.max(height, getSystemDimen(system, "navigation_bar_gesture_height"));
    height = Math.max(height, getSystemDimen(system, "navigation_bar_frame_height"));
    if (height == 0) {
      height = (int) (48 * context.getResources().getDisplayMetrics().density);
    }
    return height;
  }

  private static int getSystemDimen(Resources resources, String name) {
    int resourceId = resources.getIdentifier(name, "dimen", "android");
    return resourceId > 0 ? resources.getDimensionPixelSize(resourceId) : 0;
  }

  private static void applyImeBottomInset(View inputView, int bottomInset) {
    int previousBottomInset = 0;
    if (inputView instanceof MozcView) {
      previousBottomInset = ((MozcView) inputView).getNavigationBarBottomInset();
      ((MozcView) inputView).setNavigationBarBottomInset(bottomInset);
    }

    View keyboardContainer = inputView.findViewById(R.id.keyboard_container);
    if (keyboardContainer != null) {
      ViewGroup.LayoutParams layoutParams = keyboardContainer.getLayoutParams();
      if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
        ViewGroup.MarginLayoutParams marginLayoutParams =
            (ViewGroup.MarginLayoutParams) layoutParams;
        if (marginLayoutParams.bottomMargin != bottomInset) {
          marginLayoutParams.bottomMargin = bottomInset;
          keyboardContainer.setLayoutParams(marginLayoutParams);
        }
      }
    }

    if (inputView.getPaddingBottom() != 0) {
      inputView.setPadding(
          inputView.getPaddingLeft(),
          inputView.getPaddingTop(),
          inputView.getPaddingRight(),
          0);
    }

    if (previousBottomInset != bottomInset) {
      inputView.requestLayout();
    }
  }
}