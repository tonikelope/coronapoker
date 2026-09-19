package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.table.TableCommandSink;
import com.tonikelope.coronapoker.table.TableRenderer;
import com.tonikelope.coronapoker.table.TableRendererProvider;

/** Service-loader entry for the native GDX table. */
public final class GdxTableRendererProvider implements TableRendererProvider {

    @Override
    public String id() {
        return "gdx";
    }

    @Override
    public TableRenderer create(TableCommandSink commandSink) {
        return new GdxTableRenderer(commandSink);
    }
}
