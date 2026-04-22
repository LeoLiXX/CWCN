package org.bi9clt.cwcn.core.adif;

import android.content.Context;
import android.os.Environment;

import org.bi9clt.cwcn.core.log.ConfirmedQsoLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class CwAdifFileWriter {
    private CwAdifFileWriter() {
    }

    public static File export(Context context, List<ConfirmedQsoLog> logs, String programVersion) throws IOException {
        File baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (baseDir == null) {
            baseDir = context.getFilesDir();
        }

        File exportDir = new File(baseDir, "exports");
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            throw new IOException("Unable to create export directory.");
        }

        String fileName = "cwcn-log-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date())
                + ".adi";
        File targetFile = new File(exportDir, fileName);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(targetFile),
                StandardCharsets.UTF_8
        )) {
            writer.write(CwAdifExporter.buildAdifFile(logs, programVersion));
        }
        return targetFile;
    }
}
