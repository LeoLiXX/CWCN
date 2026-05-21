package org.bi9clt.cwcn.ui.navigation;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.bi9clt.cwcn.R;

public final class FormalBottomNavStyler {
    public enum Page {
        OPERATE,
        SPECTRUM,
        LOGBOOK,
        SETTINGS
    }

    private FormalBottomNavStyler() {
    }

    public static void apply(BottomNavigationView navView, Page activePage) {
        if (navView == null) {
            return;
        }
        int desiredItemId;
        switch (activePage) {
            case OPERATE:
                desiredItemId = R.id.menu_nav_operate;
                break;
            case LOGBOOK:
                desiredItemId = R.id.menu_nav_logbook;
                break;
            case SETTINGS:
                desiredItemId = R.id.menu_nav_settings;
                break;
            case SPECTRUM:
            default:
                desiredItemId = R.id.menu_nav_spectrum;
                break;
        }
        if (navView.getSelectedItemId() != desiredItemId) {
            navView.setSelectedItemId(desiredItemId);
        }
    }
}
