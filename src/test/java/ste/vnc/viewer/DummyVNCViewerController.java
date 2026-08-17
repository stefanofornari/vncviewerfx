package ste.vnc.viewer;

import com.tigervnc.rfb.CMsgWriterV3;
import java.util.Optional;
import static org.mockito.Mockito.mock;
import ste.xtest.concurrent.SingleTaskExecutorService;

/**
 *
 */
public class DummyVNCViewerController extends VNCViewerController {

    protected String mockClipboardText = "";


    @Override
    protected VNCService newVNCService() {
        VNCServiceStub vncStub = new VNCServiceStub(Optional.of(
            new SingleTaskExecutorService(() -> vnc.connected.set(true))));
        vncStub.customWriter(mock(CMsgWriterV3.class));

        return vncStub;
    }

    @Override
    public String getClipboard() {
        return mockClipboardText;
    }

    @Override
    public void setServerClipboardText(String text) {
        this.mockClipboardText = (text != null) ? text : "";
    }
}
