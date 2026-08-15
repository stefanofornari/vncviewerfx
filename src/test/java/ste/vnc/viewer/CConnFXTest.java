package ste.vnc.viewer;


import javafx.stage.Stage;
import org.junit.jupiter.api.Disabled;
import org.testfx.framework.junit5.ApplicationTest;

/**
 * Unit tests for {@link CConnFX} connection state handling.
 *
 * These tests are currently disabled because CConnFX still opens a real
 * TCP connection. They document the desired semantics for the connected
 * property so we can validate behaviour once a fake socket is introduced.
 */
@Disabled("Depends on real TCP connection; enable after introducing fake/injectable socket for CConnFX")
class CConnFXTest extends ApplicationTest {

    @Override
    public void start(Stage stage) {
        // No UI needed for these tests; JavaFX platform is initialised by TestFX.
    }
/*
    @Test
    void constructor_does_not_throw_and_leaves_connected_false_when_server_unreachable() {
        // given: no VNC server listening on 127.0.0.1:5905
        TestImageRender imageRender = new TestImageRender();

        // when: constructing CConnFX must not throw, even if the TCP connect fails
        //CConnFX connection = new CConnFX(imageRender, () -> { });

        // then: connected property remains false
        //then(connection.connected.get()).isFalse();
    }

    @Test
    void connectionLost_sets_connected_false() {
        // given
        TestImageRender imageRender = new TestImageRender();
        //CConnFX connection = new CConnFX(imageRender, () -> { });

        // when
        //connection.connectionLost();
        WaitForAsyncUtils.waitForFxEvents();

        // then
        //then(connection.connected.get()).isFalse();
    }

    @Test
    void close_sets_connected_false() {
        // given
        TestImageRender imageRender = new TestImageRender();
        //CConnFX connection = new CConnFX(imageRender, () -> { });

        // when
        //connection.close();
        WaitForAsyncUtils.waitForFxEvents();

        // then
        //then(connection.connected.get()).isFalse();
    }
*/
}
