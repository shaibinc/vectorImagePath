/* Copyright 2015 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.jiangpeng.android.antrace.autobgremover;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * Utility class for manipulating images.
 **/
public class ImageUtils {

    public static Bitmap tfResizeBilinear(Bitmap bitmap, int w, int h) {
        if (bitmap == null) {
            return null;
        }

        Bitmap resized = Bitmap.createBitmap(w, h,
                Bitmap.Config.ARGB_8888);

        final Canvas canvas = new Canvas(resized);
        canvas.drawBitmap(bitmap,
                new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                new Rect(0, 0, w, h),
                null);

        return resized;
    }
    public static Bitmap createClippedBitmap(Bitmap bitmap, int x, int y, int width, int height) {
        if (bitmap == null) {
            return null;
        } else {
            Bitmap var5 = Bitmap.createBitmap(bitmap, x, y, width, height);
            return var5;
        }
    }
    public static Bitmap scaleBitmap(Bitmap bitmap, int destWidth, int destHeight) {
        if (bitmap == null) {
            return null;
        } else if (destWidth > 0 && destHeight > 0) {
            Bitmap var3 = bitmap;
            int var4 = bitmap.getWidth();
            int var5 = bitmap.getHeight();
            if (var4 > destWidth) {
                if (var5 > destHeight) {
                    float var8 = (float)destWidth / (float)var4;
                    float var9 = (float)destHeight / (float)var5;
                    Bitmap var10;
                    if (var8 > var9) {
                        var10 = a(bitmap, var8, var4, var5);
                        if (var10 != null) {
                            var3 = createClippedBitmap(var10, 0, (var10.getHeight() - destHeight) / 2, destWidth, destHeight);
                        }
                    } else {
                        var10 = a(bitmap, var9, var4, var5);
                        if (var10 != null) {
                            var3 = createClippedBitmap(var10, (var10.getWidth() - destWidth) / 2, 0, destWidth, destHeight);
                        }
                    }
                } else {
                    var3 = createClippedBitmap(bitmap, (bitmap.getWidth() - destWidth) / 2, 0, destWidth, var5);
                }
            } else if (var4 <= destWidth) {
                if (var5 > destHeight) {
                    var3 = createClippedBitmap(bitmap, 0, (bitmap.getHeight() - destHeight) / 2, var4, destHeight);
                } else {
                    var3 = Bitmap.createBitmap(destWidth, destHeight, bitmap.getConfig());
                    Canvas var11 = new Canvas(var3);
                    Paint var12 = new Paint(1);
                    var11.drawBitmap(bitmap, new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()), new Rect(0, 0, destWidth, destHeight), var12);
                }
            }

            return var3;
        } else {
            return bitmap;
        }
    }
    private static Bitmap a(Bitmap var0, float var1, int var2, int var3) {
        if (var0 == null) {
            return null;
        } else {
            Matrix var4 = new Matrix();
            var4.postScale(var1, var1);
            Bitmap var5 = Bitmap.createBitmap(var0, 0, 0, var2, var3, var4, true);
            return var5;
        }
    }

}
