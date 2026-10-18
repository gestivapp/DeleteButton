package com.primoceara.deletebutton;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;

import androidx.core.app.NotificationCompat;

/**
 * Serviço que mantém o botão ⌫ flutuante sobre o teclado.
 * Posição: canto superior direito da área do teclado, ao lado do microfone.
 *
 * Funcionamento:
 *  - Toque simples   → apaga 1 caractere
 *  - Toque longo     → apaga continuamente enquanto segurar (igual ao ⌫ nativo)
 */
public class OverlayService extends Service {

    private WindowManager windowManager;
    private View overlayView;

    // Tamanho e posição do botão (ajuste fino aqui se necessário)
    private static final int BUTTON_SIZE_DP  = 44;   // largura e altura do botão
    private static final int MARGIN_RIGHT_DP = 6;    // distância da borda direita
    private static final int MARGIN_TOP_DP   = 2;    // pequeno offset para alinhar com o microfone

    // Delay entre apagadas contínuas (ms)
    private static final long DELETE_REPEAT_DELAY_MS = 50;
    // Delay inicial antes de começar a repetir (ms)
    private static final long DELETE_LONG_PRESS_MS   = 400;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isDeleting = false;

    private final Runnable deleteRunnable = new Runnable() {
        @Override
        public void run() {
            if (isDeleting) {
                sendDeleteKey();
                handler.postDelayed(this, DELETE_REPEAT_DELAY_MS);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, buildNotification());
        showOverlay();
        return START_STICKY;
    }

    private void showOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Cria o botão
        ImageButton btn = new ImageButton(this);
        btn.setContentDescription("Apagar");

        // Visual: fundo semi-transparente arredondado, símbolo ⌫ branco
        btn.setBackgroundColor(Color.TRANSPARENT);
        btn.setImageDrawable(createDeleteDrawable());
        btn.setPadding(0, 0, 0, 0);

        // Parâmetros do overlay
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int px = dpToPx(BUTTON_SIZE_DP);
        int marginRight = dpToPx(MARGIN_RIGHT_DP);
        int marginTop   = dpToPx(MARGIN_TOP_DP);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                px, px,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        // Ancora no canto inferior direito e sobe a quantidade certa
        // O teclado do Google tem aprox 50dp de barra superior de ferramentas.
        // O botão fica no centro vertical dessa barra, lado direito (ao lado do microfone).
        // A posição Y é calculada a partir do bottom da tela subindo a altura do teclado + offset.
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.x = marginRight;   // distância da borda direita
        // y = teclado inteiro (~270dp) - metade da barra de ferramentas (~25dp) - metade do botão
        params.y = dpToPx(270) - dpToPx(25) - px / 2 + marginTop;

        // Toque: apaga 1 vez; segurar: apaga continuamente
        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Apaga imediatamente ao tocar
                    sendDeleteKey();
                    // Depois de LONG_PRESS_MS, começa a apagar continuamente
                    isDeleting = false;
                    handler.postDelayed(() -> {
                        isDeleting = true;
                        handler.post(deleteRunnable);
                    }, DELETE_LONG_PRESS_MS);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // Para de apagar
                    isDeleting = false;
                    handler.removeCallbacks(deleteRunnable);
                    return true;
            }
            return false;
        });

        overlayView = btn;
        windowManager.addView(overlayView, params);
    }

    /** Envia o evento de apagar para o campo de texto focado via AccessibilityService */
    private void sendDeleteKey() {
        DeleteAccessibilityService service = DeleteAccessibilityService.getInstance();
        if (service != null) {
            service.performDelete();
        }
    }

    /** Cria um drawable de texto com o símbolo ⌫ */
    private android.graphics.drawable.Drawable createDeleteDrawable() {
        // Usa um TextView como canvas para renderizar o emoji ⌫
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText("⌫");
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22);
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER);

        int size = dpToPx(BUTTON_SIZE_DP);
        tv.measure(
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY));
        tv.layout(0, 0, size, size);

        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size,
                android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);

        // Fundo arredondado semi-transparente (igual ao estilo Gboard)
        android.graphics.Paint bg = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.argb(180, 60, 60, 60));
        float radius = dpToPx(8);
        canvas.drawRoundRect(0, 0, size, size, radius, radius, bg);

        tv.draw(canvas);
        return new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private Notification buildNotification() {
        Intent notifIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, notifIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, MainActivity.CHANNEL_ID)
                .setContentTitle("Botão Delete ativo")
                .setContentText("Toque para abrir as configurações")
                .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isDeleting = false;
        handler.removeCallbacksAndMessages(null);
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
        }
    }
}
