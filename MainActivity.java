package com.tvplayer.lite;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {

    private Button btnBrowse, btnUsb, btnResume;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("tvplayer", MODE_PRIVATE);

        btnBrowse = findViewById(R.id.btn_browse_storage);
        btnUsb = findViewById(R.id.btn_browse_usb);
        btnResume = findViewById(R.id.btn_resume);

        btnBrowse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, VideoBrowserActivity.class);
                intent.putExtra("root", "/storage/emulated/0");
                startActivity(intent);
            }
        });

        btnUsb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String usbPath = findUsbPath();
                if (usbPath != null) {
                    Intent intent = new Intent(MainActivity.this, VideoBrowserActivity.class);
                    intent.putExtra("root", usbPath);
                    startActivity(intent);
                } else {
                    Toast.makeText(MainActivity.this, "No USB storage detected", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnResume.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String lastVideo = prefs.getString("last_video", null);
                if (lastVideo != null) {
                    Intent intent = new Intent(MainActivity.this, VideoPlayerActivity.class);
                    intent.putExtra("video_path", lastVideo);
                    intent.putExtra("resume", true);
                    startActivity(intent);
                }
            }
        });

        btnBrowse.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String lastVideo = prefs.getString("last_video", null);
        if (lastVideo != null) {
            btnResume.setVisibility(View.VISIBLE);
        } else {
            btnResume.setVisibility(View.GONE);
        }
    }

    private String findUsbPath() {
        java.io.File storageDir = new java.io.File("/storage");
        if (storageDir.exists() && storageDir.isDirectory()) {
            java.io.File[] files = storageDir.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    String name = f.getName();
                    if (!name.equals("emulated") && !name.equals("self") && f.isDirectory() && f.canRead()) {
                        return f.getAbsolutePath();
                    }
                }
            }
        }

        // Check /mnt/usb paths
        java.io.File mntUsb = new java.io.File("/mnt/usb_storage");
        if (mntUsb.exists() && mntUsb.isDirectory() && mntUsb.canRead()) {
            return mntUsb.getAbsolutePath();
        }

        java.io.File mntMedia = new java.io.File("/mnt/media_rw");
        if (mntMedia.exists() && mntMedia.isDirectory()) {
            java.io.File[] files = mntMedia.listFiles();
            if (files != null && files.length > 0) {
                return files[0].getAbsolutePath();
            }
        }

        return null;
    }
}
