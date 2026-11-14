package com.example.watchlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Imports a picture, shrinks it to screen size and keeps a private copy as the background. */
final class BgImage {
    private BgImage() {}

    interface Src {
        InputStream open() throws IOException;
    }

    static File file(Context c) {
        return new File(c.getFilesDir(), "bg.jpg");
    }

    static Src fromUri(final Context c, final Uri u) {
        return () -> {
            InputStream in = c.getContentResolver().openInputStream(u);
            if (in == null) throw new IOException("no stream");
            return in;
        };
    }

    static Src fromFile(final File f) {
        return () -> new FileInputStream(f);
    }

    static Bitmap load(Context c) {
        File f = file(c);
        return f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
    }

    static void delete(Context c) {
        file(c).delete();
    }

    /** Decode (downsampled), rotate per EXIF, scale to the screen and save. */
    static boolean importFrom(Context c, Src src) {
        try {
            DisplayMetrics dm = c.getResources().getDisplayMetrics();
            int target = Math.max(dm.widthPixels, dm.heightPixels); // short side we want to keep

            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = src.open()) {
                BitmapFactory.decodeStream(in, null, o);
            }
            if (o.outWidth <= 0 || o.outHeight <= 0) return false;

            int shortSide = Math.min(o.outWidth, o.outHeight);
            int sample = 1;
            while (shortSide / (sample * 2) >= target) sample *= 2;

            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap b;
            try (InputStream in = src.open()) {
                b = BitmapFactory.decodeStream(in, null, o);
            }
            if (b == null) return false;

            int deg = 0;
            if (Build.VERSION.SDK_INT >= 24) {
                try (InputStream in = src.open()) {
                    int ori = new ExifInterface(in).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    if (ori == ExifInterface.ORIENTATION_ROTATE_90) deg = 90;
                    else if (ori == ExifInterface.ORIENTATION_ROTATE_180) deg = 180;
                    else if (ori == ExifInterface.ORIENTATION_ROTATE_270) deg = 270;
                } catch (Exception ignored) {
                }
            }

            float scale = 1f;
            int ss = Math.min(b.getWidth(), b.getHeight());
            if (ss > target) scale = target / (float) ss;
            if (deg != 0 || scale != 1f) {
                Matrix m = new Matrix();
                m.postRotate(deg);
                m.postScale(scale, scale);
                Bitmap nb = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (nb != b) b.recycle();
                b = nb;
            }
            return save(c, b);
        } catch (Exception | OutOfMemoryError e) {
            return false;
        }
    }

    private static boolean save(Context c, Bitmap b) {
        try (FileOutputStream fo = new FileOutputStream(file(c))) {
            return b.compress(Bitmap.CompressFormat.JPEG, 90, fo);
        } catch (IOException e) {
            return false;
        } finally {
            b.recycle();
        }
    }
}
