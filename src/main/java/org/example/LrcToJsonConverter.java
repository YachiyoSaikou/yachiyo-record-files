package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LrcToJsonConverter {
    static final int OFFSET_MS = 0;

    static class LineEntry {
        int time;
        String text;
        String cn;
        boolean interlude;

        public LineEntry(int time, String text, String cn) {
            this.time = time;
            this.text = text;
            this.cn = cn;
        }

        public LineEntry(int time) {
            this.time = time;
            this.text = "";
            this.interlude = true;
        }
    }

    public static void main(String[] args) throws IOException {
        Path currentDir = Paths.get(System.getProperty("user.dir"));

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    // System.out.println(path);

                    File file = new File(path.toUri());
                    File json = new File(file.getName().replace(".lrc",".json"));

                    if (!file.getName().endsWith(".lrc") || json.exists()) {
                        continue;
                    }

                    try {
                        List<String> lines = readLines(file);
                        List<LineEntry> result = parseLrc(lines);

                        Gson gson = new GsonBuilder().setPrettyPrinting().create();
                        try (Writer writer = new OutputStreamWriter(new FileOutputStream(json), StandardCharsets.UTF_8)) {
                            gson.toJson(result, writer);
                        }
                        System.out.println("JSON generated in " + json.getName());
                    } catch (IOException exception) {
                        exception.printStackTrace();
                    }
                }
            }
        }
    }

    static List<String> readLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    static List<LineEntry> parseLrc(List<String> lines) {
        List<LineEntry> entries = new ArrayList<>();
        Pattern timePattern = Pattern.compile("^\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]");

        Map<Integer, List<String>> grouped = new TreeMap<>();
        for (String line : lines) {
            Matcher matcher = timePattern.matcher(line);
            if (matcher.find()) {
                int minutes = Integer.parseInt(matcher.group(1));
                int seconds = Integer.parseInt(matcher.group(2));
                int millis = Integer.parseInt(matcher.group(3));
                if (millis < 100) millis *= 10;
                int time = minutes * 60 * 1000 + seconds * 1000 + millis;

                // ✅ 应用偏移值
                time += OFFSET_MS;
                if (time < 0) time = 0;

                String text = line.replaceAll("\\[\\d{2}:\\d{2}\\.\\d{2,3}\\]", "").trim();
                grouped.computeIfAbsent(time, k -> new ArrayList<>()).add(text);
            }
        }

        boolean interludeAdded = false;
        for (Map.Entry<Integer, List<String>> entry : grouped.entrySet()) {
            int time = entry.getKey();
            List<String> texts = entry.getValue();
            String jp = texts.size() > 0 ? texts.get(0) : "";
            String cn = texts.size() > 1 ? texts.get(1) : "";
            if (!interludeAdded) {
                entries.add(new LineEntry(0));
                interludeAdded = true;
            }
            entries.add(new LineEntry(time, jp, cn));
        }

        return entries;
    }
}