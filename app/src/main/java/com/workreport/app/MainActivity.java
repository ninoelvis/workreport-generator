package com.workreport.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 工作报告生成器 —— WebView 壳。
 *
 * 新增能力（2026-10）：
 *  1) 生成加密 ZIP 并保存到系统「下载」目录后，自动把压缩包文件名复制到系统剪贴板；
 *  2) 同时自动拉起阿里邮箱 App，并把刚生成的 ZIP 作为附件带入写邮件界面。
 */
public class MainActivity extends Activity {

    private static final String TAG = "WorkReportApp";

    /** SharedPreferences 文件名与「默认发送应用」配置项 */
    private static final String PREF_NAME = "workreport_prefs";
    private static final String PREF_MAIL_PACKAGE = "preferred_mail_package";

    /** 阿里邮箱（含历史包名与钉钉内置邮箱）候选，按优先级尝试 */
    private static final String[] MAIL_PACKAGES = {
            "com.alibaba.cloudmail",     // 阿里邮箱 / 阿里云邮箱（个人版、企业版）
            "com.alibaba.android.mail", // 旧版阿里邮箱
            "com.alibaba.android.rimet"  // 钉钉（内置阿里邮箱）
    };

    /**
     * 「能接收分享但不是邮箱」的应用：这些即使注册了 ACTION_SEND 也不该出现在选择列表里。
     * 邮件客户端的可靠特征是能响应 ACTION_SENDTO + mailto:，见 resolveMailApps()。
     */
    private static final Set<String> NON_MAIL_PACKAGES = new HashSet<>(Arrays.asList(
            "com.tencent.mm",                    // 微信
            "com.tencent.mobileqq",             // QQ
            "com.tencent.qqmusic",              // QQ音乐
            "com.sina.weibo",                   // 微博
            "com.alibaba.android.rimet",         // 钉钉
            "com.eg.android.AlipayGphone",      // 支付宝
            "com.android.mms",                  // 系统短信
            "com.android.messaging",            // 短信
            "com.google.android.apps.messaging",// 信息
            "com.samsung.android.messaging",    // 三星信息
            "com.xiaomi.mms",                   // 小米短信
            "com.android.documentsui",          // 文件管理器
            "com.google.android.apps.docs"      // Google 文件
    ));

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private WebView webView;

    /** 最近一次生成的 ZIP 内容，用于拉起邮件时作为附件（避免二次 Base64 传输） */
    private volatile byte[] pendingZipBytes;
    private volatile String pendingZipName;

    /**
     * 把状态栏与导航栏涂成和页面背景一样的颜色。
     *
     * 之前这两条系统栏是系统默认的灰色，页面却是白色/浅灰，视觉上很割裂。
     * 页面主题可以在 App 内手动切换，所以颜色由网页侧通过 JS Bridge 传进来，
     * 这里只负责把色值刷到系统栏，并按明暗决定系统图标用深色还是浅色。
     */
    private void applySystemBars(final int bgColor, final boolean dark) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Window window = getWindow();
                    window.setStatusBarColor(bgColor);
                    window.setNavigationBarColor(bgColor);

                    int flags = 0;
                    if (!dark) {
                        // 浅色背景 → 系统图标用深色，否则看不清
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                        }
                    }
                    window.getDecorView().setSystemUiVisibility(flags);
                } catch (Throwable ignored) {
                    // 个别 ROM 上设置系统栏颜色会抛异常，忽略即可，不影响主功能
                }
            }
        });
    }

    /** 解析 #RRGGBB / #AARRGGBB 形式的颜色 */
    private static int parseHexColor(String hex, int fallback) {
        if (hex == null) return fallback;
        String s = hex.trim();
        if (s.isEmpty()) return fallback;
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 6) {
                return (int) (0xFF000000L | Long.parseLong(s, 16));
            } else if (s.length() == 8) {
                return (int) Long.parseLong(s, 16);
            } else if (s.length() == 3) {
                String r = s.substring(0, 1), g = s.substring(1, 2), b = s.substring(2, 3);
                return (int) (0xFF000000L
                        | Long.parseLong(r + r, 16) << 16
                        | Long.parseLong(g + g, 16) << 8
                        | Long.parseLong(b + b, 16));
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        // 状态栏 / 导航栏先给一个与页面一致的底色，避免启动瞬间闪白
        applySystemBars(0xFFF5F6F7, false);

        WebView view = new WebView(this);
        this.webView = view;

        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        if (Build.VERSION.SDK_INT >= 29) {
            settings.setForceDark(WebSettings.FORCE_DARK_AUTO);
        }

        view.setBackgroundColor(0);
        view.setWebViewClient(new WebViewClient());
        view.setWebChromeClient(new WebChromeClient());
        view.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        setContentView(view);
        view.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        WebView view = this.webView;
        if (view != null) {
            view.evaluateJavascript("(function(){var m=window.matchMedia('(prefers-color-scheme: dark)');document.documentElement.setAttribute('data-theme', m.matches?'dark':'light');})()", null);
        }
    }

    @Override
    public void onBackPressed() {
        WebView view = this.webView;
        if (view != null && view.canGoBack()) {
            this.webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        WebView view = this.webView;
        if (view != null) {
            view.destroy();
        }
        this.executor.shutdown();
        super.onDestroy();
    }

    /* ==================== JS Bridge ==================== */

    private class AndroidBridge {

        /**
         * 网页侧把当前主题的背景色传过来，同步状态栏 / 导航栏颜色。
         * dark = true 表示深色主题，此时系统图标用浅色。
         */
        @JavascriptInterface
        public void setSystemBarTheme(String hexColor, boolean dark) {
            int color = parseHexColor(hexColor, dark ? 0xFF1A1A1A : 0xFFF5F6F7);
            applySystemBars(color, dark);
        }

        /** 保存 base64 内容到系统「下载」目录（原有逻辑） */
        @JavascriptInterface
        public void downloadFile(String base64, final String fileName) {
            try {
                final byte[] bytes = Base64.decode(base64, Base64.DEFAULT);

                // 同步缓存，供后续「拉起阿里邮箱并带附件」使用
                MainActivity.this.pendingZipBytes = bytes;
                MainActivity.this.pendingZipName = sanitizeFileName(fileName);

                MainActivity.this.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final boolean ok = MainActivity.this.saveToDownloads(bytes, fileName);
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this,
                                        ok ? "已保存到下载：" + fileName : "保存失败，请重试",
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Base64 decode failed", e);
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "下载失败：数据解码错误", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        /** 把文本（压缩包文件名）复制到系统剪贴板 */
        @JavascriptInterface
        public void copyToClipboard(final String text) {
            if (text == null || text.trim().isEmpty()) {
                return;
            }
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ClipboardManager cm = (ClipboardManager)
                                MainActivity.this.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm == null) {
                            Toast.makeText(MainActivity.this, "无法访问剪贴板", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        cm.setPrimaryClip(ClipData.newPlainText("工作报告文件名", text));
                        Toast.makeText(MainActivity.this, "文件名已复制到剪贴板", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e(TAG, "copy to clipboard failed", e);
                        Toast.makeText(MainActivity.this, "复制到剪贴板失败", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        /**
         * 拉起邮箱 App 发送报告。
         * 若用户已选择过默认应用则直接使用；首次则弹出应用选择器让用户挑一个并记住。
         *
         * @return "launched" 已用默认应用发送；"picker" 首次需要用户选择；"failed" 失败
         */
        @JavascriptInterface
        public String sendToAliMail(final String fileName) {
            final String state = (MainActivity.this.getPreferredMailPackage() == null) ? "picker" : "launched";
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // 稍作延迟，确保网页端的「下载成功」弹窗先完成渲染
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                File attach = MainActivity.this.prepareAttachment(fileName);
                                if (attach == null) {
                                    Toast.makeText(MainActivity.this,
                                            "未找到压缩包，请重新生成后再试", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                String pkg = MainActivity.this.getPreferredMailPackage();
                                if (pkg == null) {
                                    // 还没设置过默认应用 → 让用户选一个（选完会持久化）
                                    MainActivity.this.showMailAppPickerForSend(attach);
                                } else {
                                    MainActivity.this.launchMailWithAttachment(attach, pkg);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "send to mail failed", e);
                                Toast.makeText(MainActivity.this, "拉起邮件失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    }, 350);
                }
            });
            return state;
        }

        /** 读取当前默认的发送应用，格式 "包名|显示名"，未设置时返回空串 */
        @JavascriptInterface
        public String getPreferredMailApp() {
            String pkg = MainActivity.this.getPreferredMailPackage();
            if (pkg == null) {
                return "";
            }
            return pkg + "|" + MainActivity.this.appLabel(pkg);
        }

        /** 手动弹出应用选择器，用于修改「默认发送应用」 */
        @JavascriptInterface
        public void showMailAppPicker() {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.showMailAppPicker();
                }
            });
        }

        /** 直接指定默认发送应用（供网页端列表选择时调用） */
        @JavascriptInterface
        public void setPreferredMailApp(final String pkg) {
            if (pkg == null || pkg.trim().isEmpty()) {
                return;
            }
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.setPreferredMailPackage(pkg);
                    String label = MainActivity.this.appLabel(pkg);
                    MainActivity.this.notifyMailAppChanged(pkg, label);
                    Toast.makeText(MainActivity.this, "已设置默认发送应用：" + label, Toast.LENGTH_SHORT).show();
                }
            });
        }

        /** 打开系统「下载」目录（原有逻辑） */
        @JavascriptInterface
        public void openDownloadFolder() {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent intent = new Intent("android.intent.action.VIEW_DOWNLOADS");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        MainActivity.this.startActivity(intent);
                    } catch (Exception e) {
                        Log.w(TAG, "ACTION_VIEW_DOWNLOADS failed, fallback", e);
                        try {
                            Intent intent2 = new Intent(Intent.ACTION_VIEW);
                            intent2.setDataAndType(Uri.parse("content://downloads/public_downloads"), "resource/folder");
                            intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            MainActivity.this.startActivity(Intent.createChooser(intent2, "打开下载目录"));
                        } catch (Exception unused) {
                            Toast.makeText(MainActivity.this,
                                    "无法自动打开，请手动打开文件管理器的「下载」目录查看", Toast.LENGTH_LONG).show();
                        }
                    }
                }
            });
        }
    }

    /* ==================== 邮件发送 ==================== */

    /**
     * 准备待分享的附件文件（cacheDir/mail_attach/xxx.zip）
     * 优先使用内存中缓存的 ZIP 数据，保证与刚生成的文件完全一致。
     */
    private File prepareAttachment(String fileName) throws Exception {
        String safeName = sanitizeFileName(fileName);
        File dir = new File(getCacheDir(), "mail_attach");
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        File target = new File(dir, safeName);

        byte[] cached = this.pendingZipBytes;
        if (cached != null && safeName.equals(this.pendingZipName)) {
            FileOutputStream fos = new FileOutputStream(target);
            try {
                fos.write(cached);
                fos.flush();
            } finally {
                fos.close();
            }
            return target;
        }
        if (target.exists() && target.length() > 0) {
            return target;
        }
        return null;
    }

    /**
     * 构造 ACTION_SEND 意图拉起指定应用并携带附件。
     *
     * @param pkg 目标应用包名；为 null 时退回到系统选择器
     */
    private void launchMailWithAttachment(File attach, String pkg) {
        Uri uri = AttachProvider.uriFor(this, attach.getName());
        String name = attach.getName();

        Intent base = new Intent(Intent.ACTION_SEND);
        base.setType("application/zip");
        base.putExtra(Intent.EXTRA_STREAM, uri);
        base.putExtra(Intent.EXTRA_SUBJECT, name);
        base.putExtra(Intent.EXTRA_TITLE, name);
        // 正文也带上文件名：万一邮件 App 未接收附件，用户仍可直接粘贴
        base.putExtra(Intent.EXTRA_TEXT, name);
        base.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            base.setClipData(ClipData.newRawUri("attachment", uri));
        } catch (Throwable ignored) {
            // 部分 ROM 上 setClipData 可能抛异常，忽略即可
        }

        // 1) 用户已指定的默认应用
        if (pkg != null && !pkg.isEmpty()) {
            String label = appLabel(pkg);
            try {
                grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {
                // 部分 ROM 不允许预授权，FLAG_GRANT_READ_URI_PERMISSION 仍然有效
            }
            Intent intent = new Intent(base);
            intent.setPackage(pkg);
            try {
                startActivity(intent);
                Toast.makeText(this, "已打开「" + label + "」，压缩包已作为附件带入", Toast.LENGTH_LONG).show();
                return;
            } catch (Exception e) {
                Log.w(TAG, "start preferred mail app failed: " + pkg, e);
            }

            // 该应用可能只收文本、不收 application/zip 附件，退化为纯文本分享
            try {
                Intent textIntent = new Intent(Intent.ACTION_SEND);
                textIntent.setType("text/plain");
                textIntent.putExtra(Intent.EXTRA_TEXT, name);
                textIntent.putExtra(Intent.EXTRA_SUBJECT, name);
                textIntent.setPackage(pkg);
                startActivity(textIntent);
                Toast.makeText(this, "已打开「" + label + "」，该应用不支持附件，请手动从「下载」目录添加：" + name,
                        Toast.LENGTH_LONG).show();
                return;
            } catch (Exception e) {
                Log.w(TAG, "start preferred mail app (text) failed: " + pkg, e);
            }

            // 默认应用已失效（多半被卸载）→ 清除设置并重新让用户选
            clearPreferredMailPackage();
            Toast.makeText(this, "默认应用「" + label + "」已无法启动，请重新选择", Toast.LENGTH_LONG).show();
            showMailAppPickerForSend(attach);
            return;
        }

        // 2) 退回到系统选择器（仍带附件）
        try {
            Intent chooser = Intent.createChooser(base, "选择邮件应用发送");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(chooser);
            Toast.makeText(this, "已打开邮件应用，压缩包已作为附件带入", Toast.LENGTH_LONG).show();
            return;
        } catch (Exception e) {
            Log.w(TAG, "chooser failed", e);
        }

        // 3) 兜底：只拉起阿里邮箱主界面，附件可在「下载」目录中手动选择
        for (String p : MAIL_PACKAGES) {
            try {
                Intent launch = getPackageManager().getLaunchIntentForPackage(p);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                    Toast.makeText(this,
                            "已打开阿里邮箱，请在「附件」中选择下载目录里的压缩包：" + name,
                            Toast.LENGTH_LONG).show();
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "launch mail app failed: " + p, e);
            }
        }

        Toast.makeText(this, "未找到可用的邮件应用，压缩包已保存在「下载」目录：" + name,
                Toast.LENGTH_LONG).show();
    }

    /* ==================== 默认发送应用（持久化） ==================== */

    private SharedPreferences prefs() {
        return getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** 读取已保存的默认发送应用；若已被卸载则清除设置并返回 null */
    private String getPreferredMailPackage() {
        String pkg = prefs().getString(PREF_MAIL_PACKAGE, "");
        if (pkg == null || pkg.isEmpty()) {
            return null;
        }
        try {
            getPackageManager().getApplicationInfo(pkg, 0);
            return pkg;
        } catch (Exception e) {
            // 应用已被卸载
            clearPreferredMailPackage();
            return null;
        }
    }

    private void setPreferredMailPackage(String pkg) {
        prefs().edit().putString(PREF_MAIL_PACKAGE, pkg).apply();
    }

    private void clearPreferredMailPackage() {
        prefs().edit().remove(PREF_MAIL_PACKAGE).apply();
    }

    private String appLabel(String pkg) {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            CharSequence label = getPackageManager().getApplicationLabel(ai);
            return label != null ? label.toString() : pkg;
        } catch (Exception e) {
            return pkg;
        }
    }

    /**
     * 探测本机可用于「写邮件 / 发送文件」的应用列表。
     *
     * 只返回邮箱类应用：优先以「能响应 ACTION_SENDTO + mailto:」为判据（邮件客户端的可靠特征），
     * 其次看应用名/包名是否带邮箱特征字样；再排除微信、QQ 这类只是「能分享文件」的应用。
     */
    private List<ApplicationInfo> resolveMailApps() {
        PackageManager pm = getPackageManager();

        // 1) 能响应 mailto: 的应用 —— 邮件客户端的强特征
        Set<String> mailtoPackages = new LinkedHashSet<>();
        try {
            Intent query = new Intent(Intent.ACTION_SENDTO);
            query.setData(Uri.parse("mailto:"));
            List<ResolveInfo> infos = pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo info : infos) {
                if (info.activityInfo != null && info.activityInfo.applicationInfo != null) {
                    mailtoPackages.add(info.activityInfo.applicationInfo.packageName);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "query mailto apps failed", e);
        }

        // 2) 汇总所有能接收分享的应用
        Map<String, ApplicationInfo> candidates = new LinkedHashMap<>();
        List<Intent> queries = new ArrayList<>();
        Intent sendText = new Intent(Intent.ACTION_SEND);
        sendText.setType("text/plain");
        queries.add(sendText);
        Intent sendAny = new Intent(Intent.ACTION_SEND);
        sendAny.setType("*/*");
        queries.add(sendAny);

        for (Intent query : queries) {
            try {
                List<ResolveInfo> infos = pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo info : infos) {
                    if (info.activityInfo == null || info.activityInfo.applicationInfo == null) {
                        continue;
                    }
                    ApplicationInfo ai = info.activityInfo.applicationInfo;
                    if (!candidates.containsKey(ai.packageName)) {
                        candidates.put(ai.packageName, ai);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "query share apps failed", e);
            }
        }

        // 3) 过滤出邮箱类应用
        List<ApplicationInfo> result = new ArrayList<>();
        for (Map.Entry<String, ApplicationInfo> entry : candidates.entrySet()) {
            String pkg = entry.getKey();
            if (NON_MAIL_PACKAGES.contains(pkg)) {
                continue;
            }
            boolean isMail = mailtoPackages.contains(pkg) || looksLikeMailApp(pkg, appLabel(pkg));
            if (isMail) {
                result.add(entry.getValue());
            }
        }

        // 阿里邮箱排前面，其余按应用名排序
        Collections.sort(result, new Comparator<ApplicationInfo>() {
            @Override
            public int compare(ApplicationInfo a, ApplicationInfo b) {
                int pa = "com.alibaba.cloudmail".equals(a.packageName) ? 0 : 1;
                int pb = "com.alibaba.cloudmail".equals(b.packageName) ? 0 : 1;
                if (pa != pb) {
                    return pa - pb;
                }
                return appLabel(a.packageName).compareToIgnoreCase(appLabel(b.packageName));
            }
        });
        return result;
    }

    /** 从应用名/包名判断是否邮箱类应用 */
    private static boolean looksLikeMailApp(String pkg, String label) {
        String s = ((pkg == null ? "" : pkg) + " " + (label == null ? "" : label)).toLowerCase(Locale.US);
        return s.contains("mail") || s.contains("email") || s.contains("inbox")
                || s.contains("outlook") || s.contains("邮箱") || s.contains("邮件");
    }

    /** 手动更改默认发送应用（用户主动触发） */
    private void showMailAppPicker() {
        final List<ApplicationInfo> apps = resolveMailApps();
        if (apps.isEmpty()) {
            Toast.makeText(this, "未找到可用的邮件应用", Toast.LENGTH_LONG).show();
            return;
        }
        final String current = getPreferredMailPackage();
        final String[] names = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            ApplicationInfo ai = apps.get(i);
            names[i] = appLabel(ai.packageName)
                    + (ai.packageName.equals(current) ? "　✓ 当前默认" : "");
        }
        new AlertDialog.Builder(this)
                .setTitle("选择邮箱应用")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String pkg = apps.get(which).packageName;
                        String label = appLabel(pkg);
                        setPreferredMailPackage(pkg);
                        notifyMailAppChanged(pkg, label);
                        Toast.makeText(MainActivity.this, "已设置默认发送应用：" + label, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 首次发送时让用户挑一个邮箱应用，挑完持久化并立即用它发送 */
    private void showMailAppPickerForSend(final File attach) {
        final List<ApplicationInfo> apps = resolveMailApps();
        if (apps.isEmpty()) {
            // 没有检测到邮箱应用 → 退回系统选择器让用户自己挑
            Toast.makeText(this, "未检测到邮箱应用，请手动选择发送方式", Toast.LENGTH_LONG).show();
            launchMailWithAttachment(attach, null);
            return;
        }
        if (apps.size() == 1) {
            // 只有一个邮箱应用，无需打扰用户，直接记住并使用
            String pkg = apps.get(0).packageName;
            String label = appLabel(pkg);
            setPreferredMailPackage(pkg);
            notifyMailAppChanged(pkg, label);
            launchMailWithAttachment(attach, pkg);
            return;
        }
        final String[] names = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            names[i] = appLabel(apps.get(i).packageName);
        }
        new AlertDialog.Builder(this)
                .setTitle("选择邮箱应用")
                .setMessage("选择后将记住该应用，下次自动用它发送。")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String pkg = apps.get(which).packageName;
                        String label = appLabel(pkg);
                        setPreferredMailPackage(pkg);
                        notifyMailAppChanged(pkg, label);
                        launchMailWithAttachment(attach, pkg);
                    }
                })
                .setNegativeButton("其它应用…", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 不改变默认设置，走系统选择器
                        launchMailWithAttachment(attach, null);
                    }
                })
                .setNeutralButton("取消", null)
                .show();
    }

    /** 通知网页端默认应用已变化 */
    private void notifyMailAppChanged(String pkg, String label) {
        WebView view = this.webView;
        if (view == null) {
            return;
        }
        String js = "window.onMailAppChanged && window.onMailAppChanged('" + escapeJs(pkg) + "','" + escapeJs(label) + "');";
        try {
            view.evaluateJavascript(js, null);
        } catch (Exception e) {
            Log.w(TAG, "notify mail app changed failed", e);
        }
    }

    private static String escapeJs(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "").replace("\r", "");
    }

    /* ==================== 保存到下载目录 ==================== */

    private boolean saveToDownloads(byte[] bytes, String fileName) {
        String safeName = sanitizeFileName(fileName);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put("_display_name", safeName);
                values.put("mime_type", "application/zip");
                values.put("relative_path", Environment.DIRECTORY_DOWNLOADS);
                Uri insertUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (insertUri == null) {
                    return false;
                }
                OutputStream out = getContentResolver().openOutputStream(insertUri);
                if (out == null) {
                    return false;
                }
                try {
                    out.write(bytes);
                    out.flush();
                } finally {
                    try {
                        out.close();
                    } catch (Exception ignored) {
                    }
                }
                return true;
            }

            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            FileOutputStream fos = new FileOutputStream(new File(dir, safeName));
            try {
                fos.write(bytes);
                fos.flush();
            } finally {
                try {
                    fos.close();
                } catch (Exception ignored) {
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "save failed", e);
            return false;
        }
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "report.zip";
        }
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (safe.isEmpty()) {
            return "report.zip";
        }
        if (safe.toLowerCase().endsWith(".zip")) {
            return safe;
        }
        return safe + ".zip";
    }
}
