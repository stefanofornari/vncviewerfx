package ste.vnc.viewer;

import com.tigervnc.rfb.AliasParameter;
import com.tigervnc.rfb.BoolParameter;
import com.tigervnc.rfb.Configuration;
import com.tigervnc.rfb.IntParameter;
import com.tigervnc.rfb.LogWriter;
import com.tigervnc.rfb.SecurityClient;
import com.tigervnc.rfb.StringParameter;
import javafx.application.Application.Parameters;
import static ste.lloop.Loop._continue_;
import static ste.lloop.Loop.on;

import dev.dirs.UserDirectories;

/**
 *
 */
public class VNCConfiguration {

    public BoolParameter noLionFS
        = new BoolParameter("NoLionFS",
            "On Mac systems, setting this parameter will force the use of the old "
            + "(pre-Lion) full-screen mode, even if the viewer is running on OS X 10.7 "
            + "Lion or later.",
            false);

    public BoolParameter embed
        = new BoolParameter("Embed",
            "If the viewer is being run as an applet, display its output to "
            + "an embedded frame in the browser window rather than to a dedicated "
            + "window. Embed=1 implies FullScreen=0 and Scale=100.",
            false);

    public BoolParameter useLocalCursor
        = new BoolParameter("UseLocalCursor",
            "Render the mouse cursor locally",
            true);
    public BoolParameter sendLocalUsername
        = new BoolParameter("SendLocalUsername",
            "Send the local username for SecurityTypes "
            + "such as Plain rather than prompting",
            true);
    public StringParameter passwordFile
        = new StringParameter("PasswordFile",
            "Password file for VNC authentication",
            "");
    public AliasParameter passwd
        = new AliasParameter("passwd",
            "Alias for PasswordFile",
            passwordFile);
    public BoolParameter autoSelect
        = new BoolParameter("AutoSelect",
            "Auto select pixel format and encoding",
            true);
    public BoolParameter fullColour
        = new BoolParameter("FullColour",
            "Use full colour - otherwise 6-bit colour is "
            + "used until AutoSelect decides the link is "
            + "fast enough",
            true);
    public AliasParameter fullColourAlias
        = new AliasParameter("FullColor",
            "Alias for FullColour",
            fullColour);
    public IntParameter lowColourLevel
        = new IntParameter("LowColorLevel",
            "Color level to use on slow connections. "
            + "0 = Very Low (8 colors), 1 = Low (64 colors), "
            + "2 = Medium (256 colors)",
            2);
    public AliasParameter lowColourLevelAlias
        = new AliasParameter("LowColourLevel",
            "Alias for LowColorLevel",
            lowColourLevel);
    public StringParameter preferredEncoding
        = new StringParameter("PreferredEncoding",
            "Preferred encoding to use (Tight, ZRLE, "
            + "hextile or raw) - implies AutoSelect=0",
            "Tight");
    public BoolParameter viewOnly
        = new BoolParameter("ViewOnly",
            "Don't send any mouse or keyboard events to "
            + "the server",
            false);
    public BoolParameter shared
        = new BoolParameter("Shared",
            "Don't disconnect other viewers upon "
            + "connection - share the desktop instead",
            false);
    public BoolParameter fullScreen
        = new BoolParameter("FullScreen",
            "Full Screen Mode",
            false);
    public BoolParameter fullScreenAllMonitors
        = new BoolParameter("FullScreenAllMonitors",
            "Enable full screen over all monitors",
            true);
    public BoolParameter acceptClipboard
        = new BoolParameter("AcceptClipboard",
            "Accept clipboard changes from the server",
            true);
    public BoolParameter sendClipboard
        = new BoolParameter("SendClipboard",
            "Send clipboard changes to the server",
            true);
    public IntParameter maxCutText
        = new IntParameter("MaxCutText",
            "Maximum permitted length of an outgoing clipboard update",
            262144);
    public StringParameter menuKey
        = new StringParameter("MenuKey",
            "The key which brings up the popup menu",
            "F8");
    public StringParameter desktopSize
        = new StringParameter("DesktopSize",
            "Reconfigure desktop size on the server on "
            + "connect (if possible)", "");
    public BoolParameter listenMode
        = new BoolParameter("listen",
            "Listen for connections from VNC servers",
            false);
    public StringParameter scalingFactor
        = new StringParameter("ScalingFactor",
            "Reduce or enlarge the remote desktop image. "
            + "The value is interpreted as a scaling factor "
            + "in percent. If the parameter is set to "
            + "\"Auto\", then automatic scaling is "
            + "performed. Auto-scaling tries to choose a "
            + "scaling factor in such a way that the whole "
            + "remote desktop will fit on the local screen. "
            + "If the parameter is set to \"FixedRatio\", "
            + "then automatic scaling is performed, but the "
            + "original aspect ratio is preserved.",
            "100");
    public BoolParameter alwaysShowServerDialog
        = new BoolParameter("AlwaysShowServerDialog",
            "Always show the server dialog even if a server "
            + "has been specified in an applet parameter or on "
            + "the command line",
            false);
    public StringParameter vncServerName
        = new StringParameter("Server",
            "The VNC server <host>[:<dpyNum>] or "
            + "<host>::<port>",
            null);
    public IntParameter vncServerPort
        = new IntParameter("Port",
            "The VNC server's port number, assuming it is on "
            + "the host from which the applet was downloaded",
            0);
    public BoolParameter acceptBell
        = new BoolParameter("AcceptBell",
            "Produce a system beep when requested to by the server.",
            true);
    public StringParameter via
        = new StringParameter("Via",
            "Automatically create an encrypted TCP tunnel to "
            + "the gateway machine, then connect to the VNC host "
            + "through that tunnel. By default, this option invokes "
            + "SSH local port forwarding using the embedded JSch "
            + "client, however an external SSH client may be specified "
            + "using the \"-extSSH\" parameter. Note that when using "
            + "the -via option, the VNC host machine name should be "
            + "specified from the point of view of the gateway machine, "
            + "e.g. \"localhost\" denotes the gateway, "
            + "not the machine on which the viewer was launched. "
            + "See the System Properties section below for "
            + "information on configuring the -Via option.", null);
    public BoolParameter tunnel
        = new BoolParameter("Tunnel",
            "The -Tunnel command is basically a shorthand for the "
            + "-via command when the VNC server and SSH gateway are "
            + "one and the same. -Tunnel creates an SSH connection "
            + "to the server and forwards the VNC through the tunnel "
            + "without the need to specify anything else.", false);
    public BoolParameter extSSH
        = new BoolParameter("extSSH",
            "By default, SSH tunneling uses the embedded JSch client "
            + "for tunnel creation. This option causes the client to "
            + "invoke an external SSH client application for all tunneling "
            + "operations. By default, \"/usr/bin/ssh\" is used, however "
            + "the path to the external application may be specified using "
            + "the -SSHClient option.", false);
    public StringParameter extSSHClient
        = new StringParameter("extSSHClient",
            "Specifies the path to an external SSH client application "
            + "that is to be used for tunneling operations when the -extSSH "
            + "option is in effect.", "/usr/bin/ssh");
    public StringParameter extSSHArgs
        = new StringParameter("extSSHArgs",
            "Specifies the arguments string or command template to be used "
            + "by the external SSH client application when the -extSSH option "
            + "is in effect. The string will be processed according to the same "
            + "pattern substitution rules as the VNC_TUNNEL_CMD and VNC_VIA_CMD "
            + "system properties, and can be used to override those in a more "
            + "command-line friendly way. If not specified, then the appropriate "
            + "VNC_TUNNEL_CMD or VNC_VIA_CMD command template will be used.", null);
    public StringParameter sshConfig
        = new StringParameter("SSHConfig",
            "Specifies the path to an OpenSSH configuration file that to "
            + "be parsed by the embedded JSch SSH client during tunneling "
            + "operations.", UserDirectories.get().homeDir + "/.ssh/config");
    public StringParameter sshKey
        = new StringParameter("SSHKey",
            "When using the Via or Tunnel options with the embedded SSH client, "
            + "this parameter specifies the text of the SSH private key to use when "
            + "authenticating with the SSH server. You can use \\n within the string "
            + "to specify a new line.", null);
    public StringParameter sshKeyFile
        = new StringParameter("SSHKeyFile",
            "When using the Via or Tunnel options with the embedded SSH client, "
            + "this parameter specifies a file that contains an SSH private key "
            + "(or keys) to use when authenticating with the SSH server. If not "
            + "specified, ~/.ssh/id_dsa or ~/.ssh/id_rsa will be used (if they exist). "
            + "Otherwise, the client will fallback to prompting for an SSH password.",
            null);
    public StringParameter sshKeyPass
        = new StringParameter("SSHKeyPass",
            "When using the Via or Tunnel options with the embedded SSH client, "
            + "this parameter specifies the passphrase for the SSH key.", null);
    public BoolParameter customCompressLevel
        = new BoolParameter("CustomCompressLevel",
            "Use custom compression level. "
            + "Default if CompressLevel is specified.",
            false);
    public IntParameter compressLevel
        = new IntParameter("CompressLevel",
            "Use specified compression level "
            + "0 = Low, 6 = High",
            1);
    public BoolParameter noJpeg
        = new BoolParameter("NoJPEG",
            "Disable lossy JPEG compression in Tight encoding.",
            false);
    public IntParameter qualityLevel
        = new IntParameter("QualityLevel",
            "JPEG quality level. "
            + "0 = Low, 9 = High",
            8);
    public StringParameter x509ca
        = new StringParameter("X509CA",
            "Path to CA certificate to use when authenticating remote servers "
            + "using any of the X509 security schemes (X509None, X509Vnc, etc.). "
            + "Must be in PEM format.",
            UserDirectories.get().homeDir + "/.vnc/x509_ca.pem");
    public StringParameter x509crl
        = new StringParameter("X509CRL",
            "Path to certificate revocation list to use in conjunction with "
            + "-X509CA. Must also be in PEM format.",
            UserDirectories.get().homeDir + "/.vnc/x509_crl.pem");
    public StringParameter config
        = new StringParameter("config",
            "Specifies a configuration file to load.", null);


    public VNCConfiguration(Parameters parameters) {
        // Load user preferences
        UserPreferences.load("global");
        SecurityClient.setDefaults();

        // Process configuration files and parameters
        Configuration.enableViewerParams();

        on(parameters.getNamed()).loop((key, value) -> {
            if (key.equalsIgnoreCase("config")) {
                Configuration.load(value);
                _continue_();
            }

            if (key.equalsIgnoreCase("log")) {
                System.err.println("Log setting: " + key);
                LogWriter.setLogParams(value);
                _continue_();
            }

            Configuration.setParam(key, value);
        });
    }
}
