package nju.ycqian.phonenode;

import android.util.Size;

public interface ImagePublisher {
    void onNewImage(byte[] data, Size size);
}
