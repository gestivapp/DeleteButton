package com.primoceara.deletebutton;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    public static final String CHANNEL_ID = "delete_btn_channel";
    private Button btnOverlay, btnAccessibility, btnToggle;
    private TextView tvStatus;
    private boolean serviceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();

        btnOverlay     = findViewById(R.id.btnOverlayPermission);
        btnAccessibility = findViewById(R.id.btnAccessibility);
        btnToggle      = findViewById(R.id.btnToggleService);
        tvStatus       = findViewById(R.id.tvStatus);

        btnOverlay.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });

        btnAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this,
                    "Procure 'Botão Delete Teclado' e ative",
                    Toast.LENGTH_LONG).show();
        });

        btnToggle.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Conceda a permissão de overlay primeiro (Passo 1)", Toast.LENGTH_LONG).show();
                return;
            }
            if (!serviceRunning) {
                startOverlayService();
            } else {
                stopOverlayService();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateOverlayButtonState();
    }

    private void updateOverlayButtonState() {
        if (Settings.canDrawOverlays(this)) {
            btnOverlay.setText("✔  Overlay permitido");
            btnOverlay.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF2E7D32));
        } else {
            btnOverlay.setText("Conceder permissão de overlay");
            btnOverlay.setBackgroundTintList(null);
        }
    }

    private void startOverlayService() {
        Intent intent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        serviceRunning = true;
        btnToggle.setText("⏹  Parar botão delete");
        btnToggle.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFFC62828));
        tvStatus.setText("Status: ativo — o botão ⌫ aparece acima do teclado");
    }

    private void stopOverlayService() {
        Intent intent = new Intent(this, OverlayService.class);
        stopService(intent);
        serviceRunning = false;
        btnToggle.setText("▶  Iniciar botão delete");
        btnToggle.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF2E7D32));
        tvStatus.setText("Status: inativo");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Botão Delete",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Mantém o botão delete ativo");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
