package com.sun.faces.context;

import java.io.IOException;
import java.io.OutputStream;

import jakarta.faces.context.ResponseStream;

public class ResponseStreamWrapper extends ResponseStream {

    private final OutputStream os;

    public ResponseStreamWrapper(OutputStream os) {
        this.os = os;
    }

    @Override
    public void close() throws IOException {
        os.close();
    }

    @Override
    public void flush() throws IOException {
        os.flush();
    }

    @Override
    public void write(byte[] b) throws IOException {
        os.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        os.write(b, off, len);
    }

    @Override
    public void write(int b) throws IOException {
        os.write(b);
    }

}
