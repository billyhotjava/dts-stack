package com.yuzhi.dts.platform.web.filter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;

class AuditResponseWrapper extends HttpServletResponseWrapper {

    private final CountingServletOutputStream countingStream;
    private final PrintWriter countingWriter;

    AuditResponseWrapper(HttpServletResponse response) throws IOException {
        super(response);
        this.countingStream = new CountingServletOutputStream(response.getOutputStream());
        this.countingWriter = new PrintWriter(countingStream, true);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return countingStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        return countingWriter;
    }

    long getContentSize() {
        countingWriter.flush();
        return countingStream.getWritten();
    }

    @Override
    public void flushBuffer() throws IOException {
        countingWriter.flush();
        super.flushBuffer();
    }

    private static class CountingServletOutputStream extends ServletOutputStream {

        private final ServletOutputStream delegate;
        private long written = 0L;

        CountingServletOutputStream(ServletOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            delegate.setWriteListener(writeListener);
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            written++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            written += len;
        }

        long getWritten() {
            return written;
        }
    }
}
