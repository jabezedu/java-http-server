package com.netapps.httpserver.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps file extensions to standard MIME content types for HTTP header generation.
 * Contains mapping for more than 20 common media and document types.
 */
public final class MimeTypes {

    private static final Map<String, String> EXT_TO_MIME = new HashMap<>();

    static {
        EXT_TO_MIME.put("html", "text/html; charset=UTF-8");
        EXT_TO_MIME.put("htm", "text/html; charset=UTF-8");
        EXT_TO_MIME.put("css", "text/css; charset=UTF-8");
        EXT_TO_MIME.put("js", "application/javascript; charset=UTF-8");
        EXT_TO_MIME.put("json", "application/json; charset=UTF-8");
        EXT_TO_MIME.put("xml", "application/xml; charset=UTF-8");
        EXT_TO_MIME.put("txt", "text/plain; charset=UTF-8");
        EXT_TO_MIME.put("svg", "image/svg+xml");
        EXT_TO_MIME.put("png", "image/png");
        EXT_TO_MIME.put("jpg", "image/jpeg");
        EXT_TO_MIME.put("jpeg", "image/jpeg");
        EXT_TO_MIME.put("gif", "image/gif");
        EXT_TO_MIME.put("webp", "image/webp");
        EXT_TO_MIME.put("ico", "image/x-icon");
        EXT_TO_MIME.put("pdf", "application/pdf");
        EXT_TO_MIME.put("zip", "application/zip");
        EXT_TO_MIME.put("tar", "application/x-tar");
        EXT_TO_MIME.put("gz", "application/gzip");
        EXT_TO_MIME.put("mp4", "video/mp4");
        EXT_TO_MIME.put("mp3", "audio/mpeg");
        EXT_TO_MIME.put("wav", "audio/wav");
        EXT_TO_MIME.put("ogg", "audio/ogg");
        EXT_TO_MIME.put("woff", "font/woff");
        EXT_TO_MIME.put("woff2", "font/woff2");
        EXT_TO_MIME.put("ttf", "font/ttf");
    }

    private MimeTypes() {
    }

    /**
     * Resolves the MIME content type from a filename or path.
     *
     * @param filename name or path of the target file
     * @return standard MIME string or "application/octet-stream" if unrecognized
     */
    public static String getMimeType(String filename) {
        if (filename == null) {
            return "application/octet-stream";
        }
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx >= 0 && dotIdx < filename.length() - 1) {
            String ext = filename.substring(dotIdx + 1).toLowerCase();
            String mime = EXT_TO_MIME.get(ext);
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }
}
