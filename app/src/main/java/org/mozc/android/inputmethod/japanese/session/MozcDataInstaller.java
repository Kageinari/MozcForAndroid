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

package org.mozc.android.inputmethod.japanese.session;

import org.mozc.android.inputmethod.japanese.MozcLog;
import com.google.common.base.Preconditions;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Installs mozc.data from APK assets into app-private storage for native engine loading.
 */
final class MozcDataInstaller {
  private static final String ASSET_DATA_FILE = "mozc.data";
  private static final String INSTALLED_DATA_FILE = "mozc.data";

  private MozcDataInstaller() {}

  /**
   * Returns the absolute path to mozc.data, copying from assets when needed.
   *
   * @return installed data file path, or {@code null} if assets do not contain mozc.data
   */
  static String ensureDataFile(Context context) {
    Preconditions.checkNotNull(context);
    AssetManager assets = context.getAssets();
    long assetSize;
    try {
      assetSize = getAssetSize(assets, ASSET_DATA_FILE);
    } catch (IOException e) {
      MozcLog.w("mozc.data is not bundled in assets; conversion engine will be limited.");
      return null;
    }

    File dataFile = new File(context.getFilesDir(), INSTALLED_DATA_FILE);
    if (dataFile.exists() && dataFile.length() == assetSize) {
      return dataFile.getAbsolutePath();
    }

    try {
      copyAssetToFile(assets, ASSET_DATA_FILE, dataFile);
      MozcLog.i("Installed mozc.data to " + dataFile.getAbsolutePath());
      return dataFile.getAbsolutePath();
    } catch (IOException e) {
      MozcLog.e("Failed to install mozc.data from assets", e);
      if (dataFile.exists()) {
        return dataFile.getAbsolutePath();
      }
      return null;
    }
  }

  private static long getAssetSize(AssetManager assets, String assetName) throws IOException {
    try (InputStream in = assets.open(assetName)) {
      long size = 0;
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1) {
        size += read;
      }
      return size;
    }
  }

  private static void copyAssetToFile(AssetManager assets, String assetName, File dest)
      throws IOException {
    File parent = dest.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw new IOException("Failed to create directory: " + parent.getAbsolutePath());
    }
    try (InputStream in = assets.open(assetName);
         OutputStream out = new FileOutputStream(dest)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
    }
  }
}