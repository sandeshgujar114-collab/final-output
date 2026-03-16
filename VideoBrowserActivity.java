package com.tvplayer.lite;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class VideoBrowserActivity extends Activity {

    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<String>(Arrays.asList(
        "mp4", "mkv", "avi", "mov", "webm", "flv",
        "3gp", "ts", "m4v", "wmv", "mpg", "mpeg"
    ));

    private static final Set<String> AUDIO_EXTENSIONS = new HashSet<String>(Arrays.asList(
        "mp3", "aac", "ac3", "wav", "flac", "ogg", "m4a", "wma"
    ));

    private ListView listView;
    private TextView tvPath, tvEmpty;
    private File currentDir;
    private String rootPath;
    private List<FileItem> fileItems = new ArrayList<FileItem>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);

        listView = findViewById(R.id.list_files);
        tvPath = findViewById(R.id.tv_path);
        tvEmpty = findViewById(R.id.tv_empty);

        rootPath = getIntent().getStringExtra("root");
        if (rootPath == null) rootPath = "/storage/emulated/0";

        currentDir = new File(rootPath);
        loadDirectory(currentDir);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                FileItem item = fileItems.get(position);
                if (item.isParent) {
                    navigateUp();
                } else if (item.file.isDirectory()) {
                    loadDirectory(item.file);
                } else if (isVideoFile(item.file) || isAudioFile(item.file)) {
                    playFile(item.file);
                }
            }
        });
    }

    private void loadDirectory(File dir) {
        if (!dir.exists() || !dir.canRead()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Cannot access: " + dir.getPath());
            listView.setVisibility(View.GONE);
            return;
        }

        currentDir = dir;
        tvPath.setText(dir.getAbsolutePath());
        fileItems.clear();

        // Add parent navigation
        if (!dir.getAbsolutePath().equals(rootPath) && !dir.getAbsolutePath().equals("/")) {
            FileItem parent = new FileItem();
            parent.file = dir.getParentFile();
            parent.isParent = true;
            parent.name = "📁 .. (Go Up)";
            parent.info = "Parent directory";
            fileItems.add(parent);
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            if (fileItems.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                listView.setVisibility(View.GONE);
            }
            refreshAdapter();
            return;
        }

        List<File> dirs = new ArrayList<File>();
        List<File> mediaFiles = new ArrayList<File>();

        for (File f : files) {
            if (f.isHidden()) continue;
            if (f.isDirectory()) {
                dirs.add(f);
            } else if (isVideoFile(f) || isAudioFile(f)) {
                mediaFiles.add(f);
            }
        }

        Comparator<File> nameSort = new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        };

        Collections.sort(dirs, nameSort);
        Collections.sort(mediaFiles, nameSort);

        for (File d : dirs) {
            FileItem item = new FileItem();
            item.file = d;
            item.isParent = false;
            item.name = "📂 " + d.getName();
            int count = countMediaFiles(d);
            item.info = count > 0 ? count + " media file(s)" : "Folder";
            fileItems.add(item);
        }

        for (File f : mediaFiles) {
            FileItem item = new FileItem();
            item.file = f;
            item.isParent = false;
            String ext = getExtension(f).toUpperCase(Locale.US);
            boolean isVideo = isVideoFile(f);
            item.name = (isVideo ? "🎬 " : "🎵 ") + f.getName();
            item.info = ext + " • " + formatSize(f.length());
            fileItems.add(item);
        }

        if (fileItems.isEmpty() || (fileItems.size() == 1 && fileItems.get(0).isParent && dirs.isEmpty() && mediaFiles.isEmpty())) {
            tvEmpty.setVisibility(fileItems.size() <= 1 ? View.VISIBLE : View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
        listView.setVisibility(View.VISIBLE);

        refreshAdapter();
    }

    private void refreshAdapter() {
        listView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() { return fileItems.size(); }

            @Override
            public Object getItem(int position) { return fileItems.get(position); }

            @Override
            public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.item_file, parent, false);
                }
                FileItem item = fileItems.get(position);
                TextView tvName = convertView.findViewById(R.id.tv_name);
                TextView tvInfo = convertView.findViewById(R.id.tv_info);
                TextView tvIcon = convertView.findViewById(R.id.tv_icon);

                tvName.setText(item.name);
                tvInfo.setText(item.info);

                if (item.isParent) {
                    tvIcon.setText("⬆");
                } else if (item.file.isDirectory()) {
                    tvIcon.setText("📂");
                } else if (isVideoFile(item.file)) {
                    tvIcon.setText("🎬");
                } else {
                    tvIcon.setText("🎵");
                }

                return convertView;
            }
        });

        if (fileItems.size() > 0) {
            listView.requestFocus();
            listView.setSelection(0);
        }
    }

    private void navigateUp() {
        File parent = currentDir.getParentFile();
        if (parent != null && parent.canRead()) {
            loadDirectory(parent);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!currentDir.getAbsolutePath().equals(rootPath)) {
                navigateUp();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void playFile(File file) {
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra("video_path", file.getAbsolutePath());
        intent.putExtra("resume", false);
        startActivity(intent);
    }

    private int countMediaFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File f : files) {
            if (isVideoFile(f) || isAudioFile(f)) count++;
        }
        return count;
    }

    static boolean isVideoFile(File file) {
        return VIDEO_EXTENSIONS.contains(getExtension(file));
    }

    static boolean isAudioFile(File file) {
        return AUDIO_EXTENSIONS.contains(getExtension(file));
    }

    static String getExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return name.substring(dot + 1).toLowerCase(Locale.US);
        }
        return "";
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    static class FileItem {
        File file;
        boolean isParent;
        String name;
        String info;
    }
}
