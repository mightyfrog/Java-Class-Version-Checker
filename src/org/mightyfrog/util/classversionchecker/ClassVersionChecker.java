package org.mightyfrog.util.classversionchecker;

import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.SystemColor;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.InvalidDnDOperationException;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.TransferHandler;

/**
 *
 * @author Shigehiro Soejima
 */
public class ClassVersionChecker extends JFrame {
    //
    private final JTextArea TA = new JTextArea() {
            //
            private final String STR = I18N.get("message.0");

            {
                setEditable(false);
                setTransferHandler(new DataTransferHandler());
            }

            /** */
            @Override
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                
                if (getText().length() == 0) {
                    int w = getWidth();
                    int h = getHeight();
                    int sw = g.getFontMetrics().stringWidth(STR);
                    g.setColor(SystemColor.textInactiveText);
                    g.drawString(STR, (w - sw) /2, h / 2);
                }
            }
        };

    /**
     *
     */
    private ClassVersionChecker() {
        setTitle(I18N.get("frame.title"));
        setIconImage(new ImageIcon(ClassVersionChecker.class.
                                   getResource("icon.png")).getImage());

        add(new JScrollPane(TA));

        setSize(400, 200);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
    }

    /**
     *
     */
    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
                /** */
                @Override
                public void run() {
                    new ClassVersionChecker();
                }
            });
    }

    //
    //
    //

    /**
     *
     * @param file
     */
    private void getVersionInfo(File file) {
        TA.append(file.getPath() + "\n");
        if (file.length() == 0) {
            TA.append(I18N.get("message.2") + "\n");
            return;
        }
        DataInputStream in = null;
        JarFile jarFile = null;
        try {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".class")) {
                in = new DataInputStream(new FileInputStream(file));
            } else if (name.endsWith(".jar")) {
                jarFile = new JarFile(file);
                Enumeration enm = jarFile.entries();
                boolean hasClassFile = false;
                while (enm.hasMoreElements()) {
                    JarEntry entry = (JarEntry) enm.nextElement();
                    if (entry.getName().toLowerCase().endsWith(".class")) {
                        in = new DataInputStream(jarFile.getInputStream(entry));
                        hasClassFile = true;
                        break;
                    }
                }
                if (!hasClassFile) {
                    TA.append(I18N.get("message.3"));
                    return;
                }
            } else {
                TA.append(I18N.get("message.4"));
                return;
            }
            int magic = in.readInt();
            if (magic != 0xCAFEBABE) {
                TA.append(I18N.get("message.5"));
                return;
            }
            int minorVersion = in.readUnsignedShort();
            int majorVersion = in.readUnsignedShort();
            String jdkVersion = "1.6";
            switch (majorVersion) {
            case 50:
                jdkVersion = "1.6";
                break;
            case 49:
                jdkVersion = "1.5";
                break;
            case 48:
                jdkVersion = "1.4";
                break;
            case 47:
                jdkVersion = "1.3";
                break;
            case 46:
                jdkVersion = "1.2";
                break;
            case 45:
                jdkVersion = "1.1";
                break;
            default:
                jdkVersion = "unknown";
            }
            TA.append(I18N.get("message.1", minorVersion, majorVersion,
                               jdkVersion));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException e) {
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
            TA.append("\n\n");
        }
    }

    /**
     *
     */
    private class DataTransferHandler extends TransferHandler {
        /** */
        @Override
        public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
            return true;
        }

        /** */
        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(JComponent comp, Transferable t) {
            try {
                List<File> list = null;
                if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    list = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                } else {
                    String data =
                        (String) t.getTransferData(createURIListFlavor());
                    list = textURIListToFileList(data);
                }
                Collections.sort(list);
                for (int i = 0; i < list.size(); i++) {
                    getVersionInfo(list.get(i));
                }
            } catch (UnsupportedFlavorException e) {
                return false;
            } catch (IOException e) {
                return false;
            }

            return true;
        }
    }

    //
    //
    //

    /**
     *
     */
    private final DataFlavor createURIListFlavor() {
        DataFlavor df = null;
        try {
            df = new DataFlavor("text/uri-list;class=java.lang.String");
        } catch (ClassNotFoundException e) {
            // shouldn't happen
        }

        return df;
    }

    /**
     *
     * @param uriList
     */
    private final List<File> textURIListToFileList(String uriList) {
        List<File> list = new ArrayList<File>(1);
        StringTokenizer st = new StringTokenizer(uriList, "\r\n");
        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            if (s.startsWith("#")) {
                // the line is a comment (as per the RFC 2483)
                continue;
            }
            try {
                URI uri = new URI(s);
                File file = new File(uri);
                if (file.length() != 0) {
                    list.add(file);
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                // ignore
            }
        }

        return list;
    }
}
