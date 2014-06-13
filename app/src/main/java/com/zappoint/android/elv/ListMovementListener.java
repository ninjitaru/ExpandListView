package com.zappoint.android.elv;

import android.view.View;

/**
 * Created by jason on 6/12/14.
 */
public interface ListMovementListener {

    public void onScrollDown();

    public void onScrollUp();

    public void onEndOfListReached();

    public void onItemRatioChanged(View view, float ratio);

    public void onItemIndexChanged(int index);

}
