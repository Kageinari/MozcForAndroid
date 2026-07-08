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
   * <p>Gesture navigation often reports a small {@code navigationBars} inset while still
   * obscuring a larger area at the bottom of the screen. The inset therefore uses the maximum
   * of navigation bar, tappable element, and mandatory system gesture insets.
   *
   * <p>The bottom inset is consumed after applying margin so the framework does not apply it
   * again. See {@code ThemedNavBarKeyboard} sample in AOSP.
   */
  public static void applyImeNavigationBarInsets(View inputView) {
    if (inputView == null) {
      return;
    }
    ViewCompat.setOnApplyWindowInsetsListener(inputView,
        (view, windowInsets) -> {
          int bottomInset = getImeBottomInset(windowInsets);
          applyImeBottomInset(view, bottomInset);
          return windowInsets.inset(0, 0, 0, bottomInset);
        });
    ViewCompat.requestApplyInsets(inputView);
  }

  /** Returns the bottom inset for {@code view}, or 0 if unavailable. */
  public static int getNavigationBarBottomInset(View view) {
    if (view == null) {
      return 0;
    }
    WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(view);
    return getImeBottomInset(insets);
  }

  private static int getImeBottomInset(WindowInsetsCompat windowInsets) {
    if (windowInsets == null) {
      return 0;
    }
    int bottom = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
    bottom = Math.max(
        bottom, windowInsets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      bottom = Math.max(
          bottom,
          windowInsets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).bottom);
    }
    return bottom;
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