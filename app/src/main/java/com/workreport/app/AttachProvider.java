package com.workreport.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 把 cacheDir/mail_attach 下的加密 ZIP 以 content:// 形式共享出去，
 * 供阿里邮箱（以及其它邮件 App）在 ACTION_SEND 中作为附件读取。
 *
 * 不依赖 androidx，纯粹使用 framework 自带的 ContentProvider。
 */
public class AttachProvider extends ContentProvider {

    public static final String AUTHORITY = "com.workreport.app.attach";
    private static final String ATTACH_DIR = "mail_attach";

    public static Uri uriFor(Context context, String fileName) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(fileName)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File resolve(Uri uri) {
        if (uri == null) {
            return null;
        }
        String segment = uri.getLastPathSegment();
        if (segment == null || segment.isEmpty()) {
            return null;
        }
        // 只取纯文件名，杜绝路径穿越
        String safeName = new File(segment).getName();
        if (safeName.isEmpty()) {
            return null;
        }
        Context context = getContext();
        if (context == null) {
            return null;
        }
        File file = new File(new File(context.getCacheDir(), ATTACH_DIR), safeName);
        return file.exists() && file.isFile() ? file : null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = resolve(uri);
        String[] columns = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        if (file != null) {
            cursor.addRow(new Object[]{file.getName(), file.length()});
        }
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "application/zip";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = resolve(uri);
        if (file == null) {
            throw new FileNotFoundException("attachment not found: " + uri);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }
}
