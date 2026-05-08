package org.bi9clt.cwcn.ui.navigation;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.bi9clt.cwcn.R;

public final class FormalBottomNavStyler {
    public enum Page {
        OPERATE,
        SPECTRUM
    }

    private FormalBottomNavStyler() {
    }

    public static void apply(BottomNavigationView navView, Page activePage) {
        if (navView == null) {
            return;
        }
        int desiredItemId = activePage == Page.OPERATE
                ? R.id.menu_nav_operate
                : R.id.menu_nav_spectrum;
        if (navView.getSelectedItemId() != desiredItemId) {
            navView.setSelectedItemId(desiredItemId);
        }
    }
}
