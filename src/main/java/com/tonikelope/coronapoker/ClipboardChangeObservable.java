package com.tonikelope.coronapoker;

import java.awt.datatransfer.Transferable;

/**
 *
 * @author tonikelope
 */
public interface ClipboardChangeObservable {

    void attachObserver(ClipboardChangeObserver observer);

    void detachObserver(ClipboardChangeObserver observer);

    Transferable getContents();

    void notifyChangeToMyObservers();

}
