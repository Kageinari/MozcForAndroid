package org.mozc.android.inputmethod.japanese;

import static org.junit.Assert.assertEquals;

import com.google.protobuf.ByteString;
import org.junit.Test;

public class MozcUtilTest {

  @Test
  public void utf8CStyleByteStringToString_stopsAtNullTerminator() {
    ByteString value = ByteString.copyFromUtf8("hello\0world");
    assertEquals("hello", MozcUtil.utf8CStyleByteStringToString(value));
  }

  @Test
  public void utf8CStyleByteStringToString_withoutTerminator() {
    ByteString value = ByteString.copyFromUtf8("mozc");
    assertEquals("mozc", MozcUtil.utf8CStyleByteStringToString(value));
  }
}