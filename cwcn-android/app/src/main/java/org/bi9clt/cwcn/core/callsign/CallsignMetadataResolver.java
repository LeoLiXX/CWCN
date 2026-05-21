package org.bi9clt.cwcn.core.callsign;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

public final class CallsignMetadataResolver {
    private static final String CTY_ASSET_NAME = "cty.dat";
    private static final String COUNTRY_CN_ASSET_NAME = "country_en2cn.dat";

    private static final Object DATASET_LOCK = new Object();
    private static volatile CallsignMetadataDataset sharedDataset;

    private final Context appContext;

    public CallsignMetadataResolver(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Nullable
    public CallsignMetadata resolve(@Nullable String callsign) {
        CallsignMetadataDataset dataset = loadSharedDataset();
        return dataset == null ? null : dataset.resolve(callsign);
    }

    @Nullable
    private CallsignMetadataDataset loadSharedDataset() {
        if (sharedDataset != null) {
            return sharedDataset;
        }
        synchronized (DATASET_LOCK) {
            if (sharedDataset != null) {
                return sharedDataset;
            }
            AssetManager assetManager = appContext.getAssets();
            try (
                    InputStream ctyInputStream = assetManager.open(CTY_ASSET_NAME);
                    InputStream countryCnInputStream = assetManager.open(COUNTRY_CN_ASSET_NAME)
            ) {
                sharedDataset = CallsignMetadataDataset.fromAssetStreams(
                        ctyInputStream,
                        countryCnInputStream
                );
            } catch (IOException ignored) {
                sharedDataset = null;
            }
            return sharedDataset;
        }
    }
}
