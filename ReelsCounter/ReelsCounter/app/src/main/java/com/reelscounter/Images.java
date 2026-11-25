package com.reelscounter;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** The photos you pick for the break screen (stored as links to your gallery, not copies). */
final class Images {

    static final int MAX = 12;

    static List<Uri> list(SharedPreferences p) {
        List<Uri> out = new ArrayList<>();
        String saved = p.getString("images", "");
        if (saved == null || saved.isEmpty()) return out;
        for (String s : saved.split("\n")) {
            if (!s.trim().isEmpty()) out.add(Uri.parse(s.trim()));
        }
        return out;
    }

    static void save(SharedPreferences p, List<Uri> uris) {
        StringBuilder sb = new StringBuilder();
        for (Uri u : uris) sb.append(u.toString()).append('\n');
        p.edit().putString("images", sb.toString()).apply();
    }

    /** Loads a photo scaled down to about maxPx on its longest side. Returns null if it can't be read. */
    static Bitmap load(Context context, Uri uri, int maxPx) {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.Source source = ImageDecoder.createSource(context.getContentResolver(), uri);
                return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    int longest = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                    int sample = 1;
                    while (longest / (sample * 2) >= maxPx) sample *= 2;
                    decoder.setTargetSampleSize(sample);
                });
            }
            ContentResolver cr = context.getContentResolver();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = cr.openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (longest / (sample * 2) >= maxPx) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (InputStream in = cr.openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }
}
