package org.openpnp.machine.reference.driver.wizards;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.gui.support.IntegerConverter;
import org.openpnp.machine.reference.driver.SmallSmtExtServerDriver;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;
import org.openpnp.gui.components.ComponentDecorators;

public class SmallSmtExtServerWizard extends AbstractConfigurationWizard {
    private final SmallSmtExtServerDriver driver;

    public SmallSmtExtServerWizard(SmallSmtExtServerDriver driver) {
        this.driver = driver;

        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(null, "SmallSmt Ext Server Driver", TitledBorder.LEADING, TitledBorder.TOP, null, null));
        contentPanel.add(panel);
        panel.setLayout(new FormLayout(new ColumnSpec[] {
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("left:default"),
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,},
            new RowSpec[] {
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,}));

        JLabel lbl1 = new JLabel("To be able to operate SmallSmt under OpenPnp you need additional software:");
        panel.add(lbl1, "2, 2, left, default");
        
        JLabel lbl2 = new JLabel("See Github repository ( https://github.com/jarekkt/smallsmt-openpnp/ ) for details.");
        panel.add(lbl2, "2, 4, left, default");  

        JLabel lbl3 = new JLabel("Remember - you need to start the server separately from OpenPnp!!");
        panel.add(lbl3, "2, 6, left, default");  
      
    }

    @Override
    public void createBindings() {
    }
}
