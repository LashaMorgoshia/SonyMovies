package ge.kadri.tv;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import java.io.*;
import java.util.*;

/** Detect HTTP 200 responses with empty HLS segments so the player can switch tracks. */
@UnstableApi
final class VideoDataSource implements DataSource {
    static final class EmptySegmentException extends EOFException {
        EmptySegmentException() { super("The video server returned an empty HLS segment"); }
    }

    static final class ErrorPolicy extends DefaultLoadErrorHandlingPolicy {
        ErrorPolicy() { super(2); }
        @Override public boolean isEligibleForFallback(IOException error) {
            Throwable cause = error;
            while (cause != null) {
                if (cause instanceof EmptySegmentException) return true;
                cause = cause.getCause();
            }
            return super.isEligibleForFallback(error);
        }
    }

    private final DataSource upstream;
    private boolean segment;
    private long bytesRead;

    VideoDataSource(DataSource upstream) { this.upstream = upstream; }
    @Override public void addTransferListener(TransferListener listener) { upstream.addTransferListener(listener); }
    @Override public long open(DataSpec spec) throws IOException {
        String path = spec.uri.getPath();
        segment = path != null && path.toLowerCase(Locale.ROOT).matches(".*\\.(ts|m4s|m4a|aac)$");
        bytesRead = 0;
        long length = upstream.open(spec);
        if (segment && length == 0) {
            try { upstream.close(); } catch (IOException ignored) { }
            throw new EmptySegmentException();
        }
        return length;
    }
    @Override public int read(byte[] buffer, int offset, int length) throws IOException {
        int count = upstream.read(buffer, offset, length);
        if (count == C.RESULT_END_OF_INPUT && segment && bytesRead == 0 && length > 0) throw new EmptySegmentException();
        if (count > 0) bytesRead += count;
        return count;
    }
    @Override public Uri getUri() { return upstream.getUri(); }
    @Override public Map<String,List<String>> getResponseHeaders() { return upstream.getResponseHeaders(); }
    @Override public void close() throws IOException { upstream.close(); }
}
