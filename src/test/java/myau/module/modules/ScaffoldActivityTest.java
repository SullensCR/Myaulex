package myau.module.modules;

import myau.Myau;
import myau.module.ModuleManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ScaffoldActivityTest {
    private ModuleManager previous;
    private Scaffold regular;

    @Before
    public void setUp() {
        this.previous = Myau.moduleManager;
        Myau.moduleManager = new ModuleManager();
        this.regular = new Scaffold();
        Myau.moduleManager.modules.put(Scaffold.class, this.regular);
    }

    @After
    public void tearDown() {
        Myau.moduleManager = this.previous;
    }

    @Test
    public void scaffoldModulesExcludeEachOtherAndLegitBlocksSprint() {
        LegitScaffold legit = new LegitScaffold();
        Myau.moduleManager.modules.put(LegitScaffold.class, legit);
        regular.setEnabled(true);
        legit.setEnabled(true);
        assertFalse(regular.isEnabled());
        assertSame(legit, ScaffoldActivity.activeModule());
        assertTrue(ScaffoldActivity.blocksSprint());
        regular.setEnabled(true);
        assertFalse(legit.isEnabled());
        assertSame(regular, ScaffoldActivity.activeModule());
    }

    @Test
    public void activityFollowsRegularScaffold() {
        assertFalse(ScaffoldActivity.isAnyEnabled());
        assertNull(ScaffoldActivity.activeModule());
        this.regular.setEnabled(true);
        assertTrue(ScaffoldActivity.isAnyEnabled());
        assertSame(this.regular, ScaffoldActivity.activeModule());
        this.regular.setEnabled(false);
        assertFalse(ScaffoldActivity.isAnyEnabled());
        assertNull(ScaffoldActivity.activeModule());
    }
}
