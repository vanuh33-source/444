package com.jap.order;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    EditText apiKey, service, comments, link;
    TextView status;
    Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, p, p, p);
        sv.addView(root);
        setContentView(sv);

        addTitle(root, "JAP 주문기");

        addLabel(root, "① API 키");
        apiKey = addEdit(root, InputType.TYPE_CLASS_TEXT);

        addLabel(root, "서비스 ID");
        service = addEdit(root, InputType.TYPE_CLASS_NUMBER);

        addLabel(root, "② 댓글 (한 줄에 하나)");
        comments = addEdit(root, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        comments.setMinLines(3);
        comments.setGravity(Gravity.TOP | Gravity.START);

        Button save = addButton(root, "저장하기");
        Button test = addButton(root, "연결 확인");

        addLabel(root, "③ 게시물 링크 (직접 주문할 때)");
        link = addEdit(root, InputType.TYPE_CLASS_TEXT);
        Button order = addButton(root, "이 링크 주문하기");

        Button overlay = addButton(root, "화면 위 떠다니는 버튼 켜기 / 끄기");

        addLabel(root, "상태");
        status = addLabel(root, "준비됨");

        apiKey.setText(Jap.key(this));
        service.setText(Jap.service(this));
        comments.setText(Jap.comments(this));

        save.setOnClickListener(v -> { savePrefs(); toast("저장했어요"); });
        test.setOnClickListener(v -> {
            savePrefs();
            setStatus("연결 확인 중...");
            runBg(() -> { String r = Jap.balance(this); main.post(() -> setStatus(r)); });
        });
        order.setOnClickListener(v -> {
            savePrefs();
            String u = Jap.igUrl(link.getText().toString());
            if (u == null) u = link.getText().toString().trim();
            if (u.isEmpty()) { setStatus("링크를 입력하세요"); return; }
            confirmOrder(u);
        });
        overlay.setOnClickListener(v -> toggleOverlay());

        handleShare(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handleShare(i);
    }

    void handleShare(Intent i) {
        if (i == null) return;
        if (Intent.ACTION_SEND.equals(i.getAction())) {
            String text = i.getStringExtra(Intent.EXTRA_TEXT);
            String u = Jap.igUrl(text);
            if (u != null) {
                if (link != null) link.setText(u);
                confirmOrder(u);
            } else {
                setStatus("공유된 내용에서 인스타 링크를 못 찾았어요");
            }
        }
    }

    void confirmOrder(String url) {
        if (Jap.key(this).isEmpty()) { setStatus("먼저 API 키를 저장하세요"); return; }
        if (Jap.comments(this).trim().isEmpty()) { setStatus("먼저 댓글을 저장하세요"); return; }
        new AlertDialog.Builder(this)
                .setTitle("주문 확인")
                .setMessage("이 게시물에 주문할까요? (실제 잔액 차감)\n" + url)
                .setPositiveButton("주문", (d, w) -> {
                    setStatus("주문 중...");
                    runBg(() -> { String r = Jap.order(this, url); main.post(() -> setStatus(r)); });
                })
                .setNegativeButton("취소", null)
                .show();
    }

    void savePrefs() {
        Jap.prefs(this).edit()
                .putString("apiKey", apiKey.getText().toString().trim())
                .putString("service", service.getText().toString().trim())
                .putString("comments", comments.getText().toString())
                .apply();
    }

    void toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            setStatus("'다른 앱 위에 표시' 권한을 켜고 다시 눌러주세요");
            return;
        }
        Intent svc = new Intent(this, OverlayService.class);
        if (OverlayService.running) {
            stopService(svc);
            setStatus("떠다니는 버튼 껐어요");
        } else {
            savePrefs();
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
            else startService(svc);
            setStatus("떠다니는 버튼 켰어요 (인스타에서 링크 복사 후 버튼 탭)");
        }
    }

    void runBg(Runnable r) { new Thread(r).start(); }
    void setStatus(String s) { if (status != null) status.setText(s); }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    TextView addTitle(LinearLayout par, String t) {
        TextView tv = new TextView(this);
        tv.setText(t); tv.setTextSize(22); tv.setPadding(0, 0, 0, dp(8));
        par.addView(tv); return tv;
    }
    TextView addLabel(LinearLayout par, String t) {
        TextView tv = new TextView(this);
        tv.setText(t); tv.setPadding(0, dp(10), 0, dp(4));
        par.addView(tv); return tv;
    }
    EditText addEdit(LinearLayout par, int type) {
        EditText e = new EditText(this);
        e.setInputType(type);
        par.addView(e); return e;
    }
    Button addButton(LinearLayout par, String t) {
        Button bn = new Button(this);
        bn.setText(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        bn.setLayoutParams(lp);
        par.addView(bn); return bn;
    }
}
