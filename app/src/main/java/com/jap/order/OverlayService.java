package com.jap.order;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

public class OverlayService extends Service {
    public static boolean running = false;
    WindowManager wm;
    Button btn;
    Handler main = new Handler(Looper.getMainLooper());
    String lastOrdered = "";
    boolean busy = false;
    boolean longPressed = false;

    static final int ORANGE = Color.parseColor("#E87A2A");
    static final int YELLOW = Color.parseColor("#D8A13A");
    static final int GREEN  = Color.parseColor("#2BB673");
    static final int RED    = Color.parseColor("#E25C5C");
    static final int GRAY   = Color.parseColor("#7E8694");

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        startForegroundNotif();

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        btn = new Button(this);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(13);
        btn.setAllCaps(false);
        setBtn(ORANGE, "주문");

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(80), dp(80), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(16);
        lp.y = dp(300);

        final Runnable longPress = new Runnable() {
            @Override public void run() {
                longPressed = true;
                confirmStop();
            }
        };

        btn.setOnTouchListener(new View.OnTouchListener() {
            int ix, iy;
            float tx, ty;
            boolean moved;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        ix = lp.x; iy = lp.y; tx = e.getRawX(); ty = e.getRawY();
                        moved = false; longPressed = false;
                        main.postDelayed(longPress, 650);   // 길게 누르면 끄기
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - tx);
                        int dy = (int) (e.getRawY() - ty);
                        if (Math.abs(dx) > 12 || Math.abs(dy) > 12) {
                            moved = true;
                            main.removeCallbacks(longPress);  // 움직이면 끄기 취소
                        }
                        lp.x = ix + dx; lp.y = iy + dy;
                        wm.updateViewLayout(btn, lp);
                        return false;
                    case MotionEvent.ACTION_UP:
                        main.removeCallbacks(longPress);
                        if (!moved && !longPressed) onTap();
                        return true;
                }
                return false;
            }
        });

        wm.addView(btn, lp);
        Toast.makeText(this, "주문 버튼 켜짐 · 끄려면 버튼을 길게 누르세요", Toast.LENGTH_LONG).show();
    }

    void setBtn(int color, String text) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(40));
        btn.setBackground(bg);
        btn.setText(text);
    }

    void flashThenReset(int color, String text, long ms) {
        setBtn(color, text);
        main.postDelayed(new Runnable() {
            @Override public void run() {
                busy = false;
                setBtn(ORANGE, "주문");
            }
        }, ms);
    }

    // 길게 누르면 끄기 확인 (떠다니는 버튼에서 바로)
    void confirmStop() {
        setBtn(RED, "끌까요?");
        Toast.makeText(this, "끄려면 한 번 더 누르세요 (3초 안에)", Toast.LENGTH_SHORT).show();
        main.postDelayed(new Runnable() {
            @Override public void run() {
                if (!stopArmed) return;
                stopArmed = false;
                if (!busy) setBtn(ORANGE, "주문");
            }
        }, 3000);
        stopArmed = true;
    }
    boolean stopArmed = false;

    void onTap() {
        // 끄기 대기 중이면, 탭 한 번 더 = 종료
        if (stopArmed) {
            stopArmed = false;
            stopSelf();
            return;
        }
        if (busy) return;

        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        String text = null;
        try {
            if (cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0) {
                CharSequence cs = cb.getPrimaryClip().getItemAt(0).getText();
                if (cs != null) text = cs.toString();
            }
        } catch (Exception ex) { /* ignore */ }

        final String u = Jap.igUrl(text);
        if (u == null) { flashThenReset(GRAY, "링크복사", 1600); return; }
        if (u.equals(lastOrdered)) { flashThenReset(GRAY, "중복", 1400); return; }

        busy = true;
        setBtn(YELLOW, "주문중");

        final Runnable delayMark = new Runnable() {
            @Override public void run() { if (busy) setBtn(GRAY, "지연중"); }
        };
        main.postDelayed(delayMark, 4000);

        new Thread(new Runnable() {
            @Override public void run() {
                final String r = Jap.order(OverlayService.this, u);
                main.post(new Runnable() {
                    @Override public void run() {
                        main.removeCallbacks(delayMark);
                        if (r.startsWith("주문 완료")) {
                            lastOrdered = u;
                            flashThenReset(GREEN, "완료!", 1400);
                        } else if (r.contains("네트워크") || r.contains("timed out") || r.contains("timeout")) {
                            flashThenReset(GRAY, "연결끊김", 2000);
                        } else {
                            flashThenReset(RED, "실패", 1800);
                        }
                    }
                });
            }
        }).start();
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    void startForegroundNotif() {
        String ch = "jap_overlay";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel c = new NotificationChannel(ch, "JAP 떠다니는 버튼", NotificationManager.IMPORTANCE_LOW);
            if (nm != null) nm.createNotificationChannel(c);
        }
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, ch)
                : new Notification.Builder(this);
        nb.setContentTitle("JAP 주문기 실행 중")
                .setContentText("주문 버튼 사용 중 · 끄려면 버튼을 길게 누르세요")
                .setSmallIcon(android.R.drawable.ic_menu_send);
        startForeground(1, nb.build());
    }

    @Override
    public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        main.removeCallbacksAndMessages(null);
        try { if (wm != null && btn != null) wm.removeView(btn); } catch (Exception e) { /* ignore */ }
        Toast.makeText(this, "주문 버튼 꺼짐", Toast.LENGTH_SHORT).show();
    }
}
