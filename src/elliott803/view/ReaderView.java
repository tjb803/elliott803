/**
 * Elliott Model 803B Simulator
 *
 * (C) Copyright Tim Baldwin 2009
 */
package elliott803.view;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

import elliott803.hardware.Reader;
import elliott803.telecode.Telecode;
import elliott803.telecode.TelecodeInputStream;
import elliott803.view.component.DeviceMode;


/**
 * A visual representation of a tape reader
 *
 * @author Baldwin
 */
public class ReaderView extends TapeDeviceView {
    private static final long serialVersionUID = 1L;

    Reader reader;

    public ReaderView(Reader reader, int id) {
        super("Reader", "Input", TapeDeviceView.DEV_LOAD, id);
        this.reader = reader;
        reader.setView(this);
    }

    // Need to implement setTape to handle new tape loaded
    void setTape(File file, String fmode, boolean ascii) {
        if (file == null) {
            reader.setTape(null);
        } else {
            try {
                // Check the tape format, if AUTO mode was selected
                if (fmode.equals(DeviceMode.MODE_AUTO)) {
                    fmode = (Telecode.isTelecode(file) ? DeviceMode.MODE_ELLIOTT : DeviceMode.MODE_SYSTEM);
                    mode.setMode(fmode);
                }

                InputStream input = null;
                if (fmode.equals(DeviceMode.MODE_ELLIOTT)) {
                    input = new FileInputStream(file);
                } else {
                    input = new TelecodeInputStream(new FileReader(file));
                }
                reader.setTape(input);
            } catch (IOException e) {
                System.err.println(e);
            }
        }
    }
}
