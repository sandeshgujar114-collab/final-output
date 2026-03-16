package com.tvplayer.lite;

import android.app.Activity;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoPlayerActivity extends Activity implements
        SurfaceHolder.Callback, MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private SurfaceView surfaceView;
    private MediaPlayer mediaPlayer;
    private SurfaceHolder surfaceHolder;

    private View controlsOverlay;
    private TextView tvTitle, tvCurrentTime, tvTotalTime, tvPlayIndicator;
    private TextView tvSubtitle, tvBoostStatus;
    private SeekBar seekBar;
    private View subtitlePopup, audioPopup;
    private ListView listSubtitles, listAudioTracks;
    private Button btnToggleBoost;

    private Handler handler = new Handler();
    private Handler subtitleHandler = new Handler();
    private SharedPreferences prefs;

    private String videoPath;
    private boolean isPlaying = false;
    private boolean controlsVisible = false;
    private boolean isPrepared = false;
    private int videoDuration = 0;

    // Audio boost
    private boolean audioBoostEnabled = false;
    private Object loudnessEnhancer; // LoudnessEnhancer (API 19+)
    private static final int BOOST_GAIN_MB = 1500; // 15dB boost

    // Subtitles
    private List<SubtitleEntry> subtitleEntries = new ArrayList<SubtitleEntry>();
    private List<File> availableSubtitleFiles = new ArrayList<File>();
    private File currentSubtitleFile = null;
    private boolean subtitlesEnabled = true;

    // Controls auto-hide
    private static final int CONTROLS_TIMEOUT = 4000;
    private static final int SEEK_AMOUNT = 10000; // 10 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        prefs = getSharedPreferences("tvplayer", MODE_PRIVATE);

        initViews();
        initSurface();

        videoPath = getIntent().getStringExtra("video_path");
        if (videoPath == null) {
            Toast.makeText(this, "No video file specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Set title
        File videoFile = new File(videoPath);
        tvTitle.setText(videoFile.getName());

        // Save as last video
        prefs.edit().putString("last_video", videoPath).apply();

        // Find subtitle files
        findSubtitleFiles(videoFile);
    }

    private void initViews() {
        surfaceView = findViewById(R.id.surface_view);
        controlsOverlay = findViewById(R.id.controls_overlay);
        tvTitle = findViewById(R.id.tv_title);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        tvPlayIndicator = findViewById(R.id.tv_play_indicator);
        tvSubtitle = findViewById(R.id.tv_subtitle);
        tvBoostStatus = findViewById(R.id.tv_boost_status);
        seekBar = findViewById(R.id.seek_bar);
        subtitlePopup = findViewById(R.id.subtitle_popup);
        audioPopup = findViewById(R.id.audio_popup);
        listSubtitles = findViewById(R.id.list_subtitles);
        listAudioTracks = findViewById(R.id.list_audio_tracks);
        btnToggleBoost = findViewById(R.id.btn_toggle_boost);

        btnToggleBoost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAudioBoost();
            }
        });
    }

    private void initSurface() {
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        // For API 17 compatibility
        if (Build.VERSION.SDK_INT < 17) {
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
    }

    // ========== SurfaceHolder Callbacks ==========

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        initMediaPlayer();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releasePlayer();
    }

    // ========== MediaPlayer Setup ==========

    private void initMediaPlayer() {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDisplay(surfaceHolder);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setDataSource(videoPath);
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setOnErrorListener(this);
            mediaPlayer.setScreenOnWhilePlaying(true);
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(this, "Error loading: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        isPrepared = true;
        videoDuration = mp.getDuration();
        tvTotalTime.setText(formatTime(videoDuration));

        // Adjust surface to video aspect ratio
        int videoWidth = mp.getVideoWidth();
        int videoHeight = mp.getVideoHeight();
        if (videoWidth > 0 && videoHeight > 0) {
            adjustSurfaceSize(videoWidth, videoHeight);
        }

        // Resume position if requested
        boolean shouldResume = getIntent().getBooleanExtra("resume", false);
        if (shouldResume) {
            int savedPos = prefs.getInt("pos_" + videoPath, 0);
            if (savedPos > 0 && savedPos < videoDuration - 3000) {
                mp.seekTo(savedPos);
                Toast.makeText(this, "Resuming from " + formatTime(savedPos), Toast.LENGTH_SHORT).show();
            }
        }

        mp.start();
        isPlaying = true;
        startProgressUpdater();
        startSubtitleUpdater();

        // Auto-load first subtitle
        if (!availableSubtitleFiles.isEmpty() && currentSubtitleFile == null) {
            loadSubtitleFile(availableSubtitleFiles.get(0));
        }

        // Show controls briefly
        showControls();
        scheduleHideControls();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        isPlaying = false;
        // Clear saved position since video is complete
        prefs.edit().remove("pos_" + videoPath).apply();
        showControls();
        tvPlayIndicator.setText("⏹");
        Toast.makeText(this, "Playback complete", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Toast.makeText(this, "Playback error: " + what + "/" + extra, Toast.LENGTH_LONG).show();
        return true;
    }

    private void adjustSurfaceSize(int videoWidth, int videoHeight) {
        int screenWidth = getWindow().getDecorView().getWidth();
        int screenHeight = getWindow().getDecorView().getHeight();

        if (screenWidth == 0 || screenHeight == 0) {
            screenWidth = getResources().getDisplayMetrics().widthPixels;
            screenHeight = getResources().getDisplayMetrics().heightPixels;
        }

        float videoAspect = (float) videoWidth / videoHeight;
        float screenAspect = (float) screenWidth / screenHeight;

        int newWidth, newHeight;
        if (videoAspect > screenAspect) {
            newWidth = screenWidth;
            newHeight = (int) (screenWidth / videoAspect);
        } else {
            newHeight = screenHeight;
            newWidth = (int) (screenHeight * videoAspect);
        }

        ViewGroup.LayoutParams lp = surfaceView.getLayoutParams();
        lp.width = newWidth;
        lp.height = newHeight;
        surfaceView.setLayoutParams(lp);
    }

    // ========== DPAD Controls ==========

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Close popups first if open
        if (subtitlePopup.getVisibility() == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                subtitlePopup.setVisibility(View.GONE);
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        if (audioPopup.getVisibility() == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                audioPopup.setVisibility(View.GONE);
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                togglePlayPause();
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                seekRelative(-SEEK_AMOUNT);
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                seekRelative(SEEK_AMOUNT);
                return true;

            case KeyEvent.KEYCODE_DPAD_UP:
                showSubtitleOptions();
                return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                showAudioOptions();
                return true;

            case KeyEvent.KEYCODE_BACK:
                savePosition();
                finish();
                return true;

            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                togglePlayPause();
                return true;

            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (!isPlaying) togglePlayPause();
                return true;

            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (isPlaying) togglePlayPause();
                return true;

            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekRelative(-SEEK_AMOUNT);
                return true;

            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekRelative(SEEK_AMOUNT);
                return true;

            // Menu key toggles audio boost
            case KeyEvent.KEYCODE_MENU:
                toggleAudioBoost();
                return true;

            default:
                // Any other key shows controls
                if (!controlsVisible) {
                    showControls();
                    scheduleHideControls();
                    return true;
                }
                break;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void togglePlayPause() {
        if (!isPrepared || mediaPlayer == null) return;

        showControls();
        scheduleHideControls();

        if (isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            tvPlayIndicator.setText("⏸");
        } else {
            mediaPlayer.start();
            isPlaying = true;
            tvPlayIndicator.setText("▶");
        }
    }

    private void seekRelative(int ms) {
        if (!isPrepared || mediaPlayer == null) return;

        showControls();
        scheduleHideControls();

        int current = mediaPlayer.getCurrentPosition();
        int target = current + ms;
        if (target < 0) target = 0;
        if (target > videoDuration) target = videoDuration;

        mediaPlayer.seekTo(target);
        tvCurrentTime.setText(formatTime(target));
        updateSeekBar(target);
    }

    // ========== Controls Visibility ==========

    private void showControls() {
        controlsVisible = true;
        controlsOverlay.setVisibility(View.VISIBLE);
    }

    private void hideControls() {
        controlsVisible = false;
        controlsOverlay.setVisibility(View.GONE);
    }

    private Runnable hideControlsRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPlaying) {
                hideControls();
            }
        }
    };

    private void scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable);
        handler.postDelayed(hideControlsRunnable, CONTROLS_TIMEOUT);
    }

    // ========== Progress Updater ==========

    private void startProgressUpdater() {
        handler.post(progressRunnable);
    }

    private Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPrepared && mediaPlayer != null) {
                try {
                    int pos = mediaPlayer.getCurrentPosition();
                    tvCurrentTime.setText(formatTime(pos));
                    updateSeekBar(pos);
                } catch (Exception e) {
                    // Player may be released
                }
            }
            handler.postDelayed(this, 500);
        }
    };

    private void updateSeekBar(int position) {
        if (videoDuration > 0) {
            seekBar.setProgress((int) ((long) position * 1000 / videoDuration));
        }
    }

    // ========== Audio Boost ==========

    private void toggleAudioBoost() {
        audioBoostEnabled = !audioBoostEnabled;

        if (audioBoostEnabled) {
            enableAudioBoost();
            tvBoostStatus.setText("🔊 BOOST: ON");
            tvBoostStatus.setTextColor(getResources().getColor(R.color.boost_on));
            btnToggleBoost.setText("🔊 Audio Boost: ON");
            Toast.makeText(this, "Audio Boost ON (+15dB)", Toast.LENGTH_SHORT).show();
        } else {
            disableAudioBoost();
            tvBoostStatus.setText("🔊 BOOST: OFF");
            tvBoostStatus.setTextColor(getResources().getColor(R.color.boost_off));
            btnToggleBoost.setText("🔊 Audio Boost: OFF");
            Toast.makeText(this, "Audio Boost OFF", Toast.LENGTH_SHORT).show();
        }

        showControls();
        scheduleHideControls();
    }

    private void enableAudioBoost() {
        if (mediaPlayer == null) return;

        // Try LoudnessEnhancer (API 19+)
        if (Build.VERSION.SDK_INT >= 19) {
            try {
                if (loudnessEnhancer == null) {
                    Class<?> leClass = Class.forName("android.media.audiofx.LoudnessEnhancer");
                    java.lang.reflect.Constructor<?> constructor = leClass.getConstructor(int.class);
                    loudnessEnhancer = constructor.newInstance(mediaPlayer.getAudioSessionId());
                }

                Method setGain = loudnessEnhancer.getClass().getMethod("setTargetGain", int.class);
                setGain.invoke(loudnessEnhancer, BOOST_GAIN_MB);

                Method setEnabled = loudnessEnhancer.getClass().getMethod("setEnabled", boolean.class);
                setEnabled.invoke(loudnessEnhancer, true);
            } catch (Exception e) {
                // Fallback: use volume amplification
                fallbackVolumeBoost(true);
            }
        } else {
            fallbackVolumeBoost(true);
        }
    }

    private void disableAudioBoost() {
        if (Build.VERSION.SDK_INT >= 19 && loudnessEnhancer != null) {
            try {
                Method setEnabled = loudnessEnhancer.getClass().getMethod("setEnabled", boolean.class);
                setEnabled.invoke(loudnessEnhancer, false);
            } catch (Exception e) {
                // ignore
            }
        }

        fallbackVolumeBoost(false);
    }

    private void fallbackVolumeBoost(boolean boost) {
        if (mediaPlayer == null) return;

        if (boost) {
            // Max out system volume
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0);
            }
            // MediaPlayer volume at maximum
            mediaPlayer.setVolume(1.0f, 1.0f);
        } else {
            mediaPlayer.setVolume(1.0f, 1.0f);
        }
    }

    private void releaseLoudnessEnhancer() {
        if (loudnessEnhancer != null) {
            try {
                Method release = loudnessEnhancer.getClass().getMethod("release");
                release.invoke(loudnessEnhancer);
            } catch (Exception e) {
                // ignore
            }
            loudnessEnhancer = null;
        }
    }

    // ========== Subtitle System ==========

    private void findSubtitleFiles(File videoFile) {
        availableSubtitleFiles.clear();
        File dir = videoFile.getParentFile();
        if (dir == null || !dir.exists()) return;

        String videoName = videoFile.getName();
        int dot = videoName.lastIndexOf('.');
        String baseName = dot > 0 ? videoName.substring(0, dot) : videoName;

        String[] subExts = {"srt", "ass", "ssa", "sub", "vtt"};
        File[] files = dir.listFiles();
        if (files == null) return;

        // First add matching-name subtitles
        for (File f : files) {
            String name = f.getName().toLowerCase(Locale.US);
            for (String ext : subExts) {
                if (name.startsWith(baseName.toLowerCase(Locale.US)) && name.endsWith("." + ext)) {
                    availableSubtitleFiles.add(f);
                }
            }
        }

        // Then add all other subtitle files in directory
        for (File f : files) {
            if (availableSubtitleFiles.contains(f)) continue;
            String name = f.getName().toLowerCase(Locale.US);
            for (String ext : subExts) {
                if (name.endsWith("." + ext)) {
                    availableSubtitleFiles.add(f);
                }
            }
        }
    }

    private void loadSubtitleFile(File subFile) {
        subtitleEntries.clear();
        currentSubtitleFile = subFile;
        subtitlesEnabled = true;

        String name = subFile.getName().toLowerCase(Locale.US);
        try {
            if (name.endsWith(".srt")) {
                parseSRT(subFile);
            } else if (name.endsWith(".vtt")) {
                parseVTT(subFile);
            } else if (name.endsWith(".ass") || name.endsWith(".ssa")) {
                parseASS(subFile);
            } else if (name.endsWith(".sub")) {
                parseSUB(subFile);
            }

            Toast.makeText(this, "Subtitle loaded: " + subFile.getName(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error loading subtitle", Toast.LENGTH_SHORT).show();
        }
    }

    private void parseSRT(File file) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        String line;
        Pattern timePattern = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2})[,\\.](\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2})[,\\.](\\d{3})");

        long startMs = 0, endMs = 0;
        StringBuilder text = new StringBuilder();
        boolean readingText = false;

        while ((line = reader.readLine()) != null) {
            line = line.trim();

            Matcher m = timePattern.matcher(line);
            if (m.find()) {
                startMs = parseTimeToMs(m.group(1), m.group(2), m.group(3), m.group(4));
                endMs = parseTimeToMs(m.group(5), m.group(6), m.group(7), m.group(8));
                readingText = true;
                text.setLength(0);
                continue;
            }

            if (readingText) {
                if (line.isEmpty()) {
                    if (text.length() > 0) {
                        SubtitleEntry entry = new SubtitleEntry();
                        entry.startMs = startMs;
                        entry.endMs = endMs;
                        entry.text = cleanSubtitleText(text.toString().trim());
                        subtitleEntries.add(entry);
                    }
                    readingText = false;
                } else {
                    // Skip sequence numbers
                    try {
                        Integer.parseInt(line);
                        continue;
                    } catch (NumberFormatException e) {
                        // Not a number, it's subtitle text
                    }
                    if (text.length() > 0) text.append("\n");
                    text.append(line);
                }
            }
        }

        // Don't forget the last entry
        if (readingText && text.length() > 0) {
            SubtitleEntry entry = new SubtitleEntry();
            entry.startMs = startMs;
            entry.endMs = endMs;
            entry.text = cleanSubtitleText(text.toString().trim());
            subtitleEntries.add(entry);
        }

        reader.close();
    }

    private void parseVTT(File file) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        String line;
        Pattern timePattern = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2})[,\\.](\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2})[,\\.](\\d{3})");
        Pattern timePatternShort = Pattern.compile("(\\d{2}):(\\d{2})[,\\.](\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2})[,\\.](\\d{3})");

        long startMs = 0, endMs = 0;
        StringBuilder text = new StringBuilder();
        boolean readingText = false;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.equals("WEBVTT") || line.startsWith("NOTE") || line.startsWith("STYLE")) continue;

            Matcher m = timePattern.matcher(line);
            Matcher ms = timePatternShort.matcher(line);

            if (m.find()) {
                startMs = parseTimeToMs(m.group(1), m.group(2), m.group(3), m.group(4));
                endMs = parseTimeToMs(m.group(5), m.group(6), m.group(7), m.group(8));
                readingText = true;
                text.setLength(0);
            } else if (ms.find()) {
                startMs = parseTimeToMs("00", ms.group(1), ms.group(2), ms.group(3));
                endMs = parseTimeToMs("00", ms.group(4), ms.group(5), ms.group(6));
                readingText = true;
                text.setLength(0);
            } else if (readingText) {
                if (line.isEmpty()) {
                    if (text.length() > 0) {
                        SubtitleEntry entry = new SubtitleEntry();
                        entry.startMs = startMs;
                        entry.endMs = endMs;
                        entry.text = cleanSubtitleText(text.toString().trim());
                        subtitleEntries.add(entry);
                    }
                    readingText = false;
                } else {
                    if (text.length() > 0) text.append("\n");
                    text.append(line);
                }
            }
        }

        if (readingText && text.length() > 0) {
            SubtitleEntry entry = new SubtitleEntry();
            entry.startMs = startMs;
            entry.endMs = endMs;
            entry.text = cleanSubtitleText(text.toString().trim());
            subtitleEntries.add(entry);
        }

        reader.close();
    }

    private void parseASS(File file) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        String line;
        Pattern dialoguePattern = Pattern.compile("Dialogue:\\s*\\d+,(\\d+):(\\d{2}):(\\d{2})\\.(\\d{2}),(\\d+):(\\d{2}):(\\d{2})\\.(\\d{2}),.*?,.*?,.*?,.*?,.*?,.*?,(.*)");

        while ((line = reader.readLine()) != null) {
            Matcher m = dialoguePattern.matcher(line.trim());
            if (m.find()) {
                long startMs = parseTimeToMs(m.group(1), m.group(2), m.group(3), m.group(4) + "0");
                long endMs = parseTimeToMs(m.group(5), m.group(6), m.group(7), m.group(8) + "0");
                String text = m.group(9)
                    .replaceAll("\\\\N", "\n")
                    .replaceAll("\\\\n", "\n")
                    .replaceAll("\\{[^}]*\\}", "");

                SubtitleEntry entry = new SubtitleEntry();
                entry.startMs = startMs;
                entry.endMs = endMs;
                entry.text = text.trim();
                if (!entry.text.isEmpty()) {
                    subtitleEntries.add(entry);
                }
            }
        }
        reader.close();
    }

    private void parseSUB(File file) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        String line;
        // MicroDVD format: {start}{end}text
        Pattern microDvdPattern = Pattern.compile("\\{(\\d+)\\}\\{(\\d+)\\}(.*)");
        float fps = 25.0f; // Default FPS

        while ((line = reader.readLine()) != null) {
            Matcher m = microDvdPattern.matcher(line.trim());
            if (m.find()) {
                long startFrame = Long.parseLong(m.group(1));
                long endFrame = Long.parseLong(m.group(2));
                String text = m.group(3).replace("|", "\n");

                SubtitleEntry entry = new SubtitleEntry();
                entry.startMs = (long) (startFrame * 1000.0 / fps);
                entry.endMs = (long) (endFrame * 1000.0 / fps);
                entry.text = cleanSubtitleText(text);
                if (!entry.text.isEmpty()) {
                    subtitleEntries.add(entry);
                }
            }
        }
        reader.close();
    }

    private long parseTimeToMs(String h, String m, String s, String ms) {
        return Long.parseLong(h) * 3600000
             + Long.parseLong(m) * 60000
             + Long.parseLong(s) * 1000
             + Long.parseLong(ms);
    }

    private String cleanSubtitleText(String text) {
        return text.replaceAll("<[^>]*>", "")
                   .replaceAll("\\{[^}]*\\}", "")
                   .trim();
    }

    private void startSubtitleUpdater() {
        subtitleHandler.post(subtitleRunnable);
    }

    private Runnable subtitleRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPrepared && mediaPlayer != null && subtitlesEnabled && !subtitleEntries.isEmpty()) {
                try {
                    long pos = mediaPlayer.getCurrentPosition();
                    String currentText = null;

                    for (SubtitleEntry entry : subtitleEntries) {
                        if (pos >= entry.startMs && pos <= entry.endMs) {
                            currentText = entry.text;
                            break;
                        }
                    }

                    if (currentText != null) {
                        tvSubtitle.setText(currentText);
                        tvSubtitle.setVisibility(View.VISIBLE);
                    } else {
                        tvSubtitle.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    // Player may be released
                }
            } else {
                tvSubtitle.setVisibility(View.GONE);
            }
            subtitleHandler.postDelayed(this, 100);
        }
    };

    // ========== Subtitle Options Popup ==========

    private void showSubtitleOptions() {
        audioPopup.setVisibility(View.GONE);

        final List<String> options = new ArrayList<String>();
        options.add(subtitlesEnabled ? "✅ Subtitles: ON" : "❌ Subtitles: OFF");
        options.add("❌ Disable Subtitles");

        for (File f : availableSubtitleFiles) {
            String marker = (f.equals(currentSubtitleFile)) ? "▶ " : "  ";
            options.add(marker + f.getName());
        }

        if (availableSubtitleFiles.isEmpty()) {
            options.add("  (No subtitle files found)");
        }

        listSubtitles.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() { return options.size(); }
            @Override
            public Object getItem(int pos) { return options.get(pos); }
            @Override
            public long getItemId(int pos) { return pos; }
            @Override
            public View getView(int pos, View cv, ViewGroup p) {
                TextView tv = new TextView(VideoPlayerActivity.this);
                tv.setText(options.get(pos));
                tv.setTextSize(20);
                tv.setTextColor(getResources().getColor(R.color.text_white));
                tv.setPadding(16, 16, 16, 16);
                tv.setBackgroundResource(R.drawable.item_selector);
                tv.setFocusable(true);
                tv.setFocusableInTouchMode(true);
                return tv;
            }
        });

        listSubtitles.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    subtitlesEnabled = !subtitlesEnabled;
                    Toast.makeText(VideoPlayerActivity.this,
                        subtitlesEnabled ? "Subtitles enabled" : "Subtitles disabled",
                        Toast.LENGTH_SHORT).show();
                } else if (position == 1) {
                    subtitlesEnabled = false;
                    tvSubtitle.setVisibility(View.GONE);
                } else {
                    int subIndex = position - 2;
                    if (subIndex >= 0 && subIndex < availableSubtitleFiles.size()) {
                        loadSubtitleFile(availableSubtitleFiles.get(subIndex));
                    }
                }
                subtitlePopup.setVisibility(View.GONE);
            }
        });

        subtitlePopup.setVisibility(View.VISIBLE);
        listSubtitles.requestFocus();
    }

    // ========== Audio Options Popup ==========

    private void showAudioOptions() {
        subtitlePopup.setVisibility(View.GONE);

        // Update boost button text
        btnToggleBoost.setText(audioBoostEnabled ? "🔊 Audio Boost: ON" : "🔊 Audio Boost: OFF");

        final List<String> options = new ArrayList<String>();
        options.add("Default Audio Track");

        // Try to get embedded track info (API 16+)
        if (Build.VERSION.SDK_INT >= 16 && mediaPlayer != null) {
            try {
                MediaPlayer.TrackInfo[] tracks = mediaPlayer.getTrackInfo();
                int audioIdx = 0;
                for (int i = 0; i < tracks.length; i++) {
                    if (tracks[i].getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) {
                        audioIdx++;
                        String lang = "";
                        if (Build.VERSION.SDK_INT >= 19) {
                            try {
                                lang = tracks[i].getLanguage();
                            } catch (Exception e) { /* ignore */ }
                        }
                        options.add("Audio Track " + audioIdx + (lang.isEmpty() ? "" : " (" + lang + ")"));
                    }
                }
            } catch (Exception e) {
                // Some devices don't support getTrackInfo
            }
        }

        listAudioTracks.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() { return options.size(); }
            @Override
            public Object getItem(int pos) { return options.get(pos); }
            @Override
            public long getItemId(int pos) { return pos; }
            @Override
            public View getView(int pos, View cv, ViewGroup p) {
                TextView tv = new TextView(VideoPlayerActivity.this);
                tv.setText(options.get(pos));
                tv.setTextSize(20);
                tv.setTextColor(getResources().getColor(R.color.text_white));
                tv.setPadding(16, 16, 16, 16);
                tv.setBackgroundResource(R.drawable.item_selector);
                tv.setFocusable(true);
                tv.setFocusableInTouchMode(true);
                return tv;
            }
        });

        listAudioTracks.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (Build.VERSION.SDK_INT >= 16 && mediaPlayer != null && position > 0) {
                    try {
                        MediaPlayer.TrackInfo[] tracks = mediaPlayer.getTrackInfo();
                        int audioIdx = 0;
                        for (int i = 0; i < tracks.length; i++) {
                            if (tracks[i].getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) {
                                audioIdx++;
                                if (audioIdx == position) {
                                    if (Build.VERSION.SDK_INT >= 16) {
                                        mediaPlayer.selectTrack(i);
                                    }
                                    Toast.makeText(VideoPlayerActivity.this, "Selected Audio Track " + position, Toast.LENGTH_SHORT).show();
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        Toast.makeText(VideoPlayerActivity.this, "Cannot switch track", Toast.LENGTH_SHORT).show();
                    }
                }
                audioPopup.setVisibility(View.GONE);
            }
        });

        audioPopup.setVisibility(View.VISIBLE);
        btnToggleBoost.requestFocus();
    }

    // ========== Lifecycle ==========

    @Override
    protected void onPause() {
        super.onPause();
        savePosition();
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        subtitleHandler.removeCallbacksAndMessages(null);
        releasePlayer();
    }

    private void savePosition() {
        if (isPrepared && mediaPlayer != null) {
            try {
                int pos = mediaPlayer.getCurrentPosition();
                prefs.edit().putInt("pos_" + videoPath, pos).apply();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private void releasePlayer() {
        releaseLoudnessEnhancer();
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                // ignore
            }
            mediaPlayer = null;
            isPrepared = false;
        }
    }

    // ========== Utility ==========

    private String formatTime(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
    }

    static class SubtitleEntry {
        long startMs;
        long endMs;
        String text;
    }
}
