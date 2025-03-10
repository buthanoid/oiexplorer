/** *****************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ***************************************************************************** */
package fr.jmmc.oiexplorer.gui;

import fr.jmmc.jmcs.gui.component.GenericListModel;
import fr.jmmc.oiexplorer.core.gui.GenericFilterEditor;
import fr.jmmc.oiexplorer.core.model.OIFitsCollectionManager;
import fr.jmmc.oiexplorer.core.model.OIFitsCollectionManagerEvent;
import fr.jmmc.oiexplorer.core.model.OIFitsCollectionManagerEventListener;
import fr.jmmc.oiexplorer.core.model.OIFitsCollectionManagerEventType;
import fr.jmmc.oiexplorer.core.model.oi.DataType;
import fr.jmmc.oiexplorer.core.model.oi.GenericFilter;
import fr.jmmc.oiexplorer.core.model.oi.Identifiable;
import fr.jmmc.oiexplorer.core.model.oi.SubsetDefinition;
import fr.jmmc.oiexplorer.core.model.plot.Range;
import fr.jmmc.oitools.OIFitsProcessor;
import fr.jmmc.oitools.model.DataModel;
import fr.jmmc.oitools.processing.Selector;
import fr.jmmc.oitools.processing.SelectorResult;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import fr.jmmc.jmcs.gui.util.ResourceImage;

public class GenericFiltersPanel extends javax.swing.JPanel
        implements OIFitsCollectionManagerEventListener, ChangeListener, ActionListener {

    /** default serial UID for Serializable interface */
    private static final long serialVersionUID = 1;

    /** Logger */
    private static final Logger logger = LoggerFactory.getLogger(GenericFiltersPanel.class);

    /** OIFitsCollectionManager singleton reference */
    private final static OIFitsCollectionManager OCM = OIFitsCollectionManager.getInstance();

    /** List of GenericFilterEditor for each GenericFilter in the current SubsetDefinition */
    private final transient List<GenericFilterEditor> genericFilterEditorList;

    /** Store all column choices available */
    private final transient List<String> columnChoices = new LinkedList<String>();

    /** when true, disables handler of Changes set on GenericFilterEditors. Used in updateGUI(). */
    private boolean updatingGUI = false;

    /** Creates new form GenericFiltersPanel */
    public GenericFiltersPanel() {
        logger.debug("creates GenericFiltersPanel");
        initComponents();
        genericFilterEditorList = new ArrayList<>(1);
        jComboBoxColumnName.setModel(new GenericListModel<String>(columnChoices, true));
        OCM.getSubsetDefinitionChangedEventNotifier().register(this);
    }

    /** Removes listeners references */
    @Override
    public void dispose() {
        genericFilterEditorList.forEach(GenericFilterEditor::dispose);
        OCM.unbind(this);
    }

    /** Updates OIExplorer Model from the GUI values. Here it updates the generic filters. Called when there is a change
     * on GenericFilterEditors. */
    private void updateModel() {
        logger.debug("updates Model");

        final SubsetDefinition subsetDefinitionCopy = OCM.getCurrentSubsetDefinition();

        final List<GenericFilter> filters = subsetDefinitionCopy.getGenericFilters();
        filters.clear();

        // take every genericFilter value from the genericFilterEditors and put it in a SubsetDefinition copy
        for (final GenericFilterEditor genericFilterEditor : this.genericFilterEditorList) {
            final GenericFilter genericFilterCopy = Identifiable.clone(genericFilterEditor.getGenericFilter());
            filters.add(genericFilterCopy);
        }
        OCM.updateSubsetDefinition(null, subsetDefinitionCopy);
    }

    /** Updates GenericFilterEditors from the OIExplorer Model values. Called when a SUBSET_CHANGED event is received */
    private void updateGUI() {
        logger.debug("updates GUI");

        try {
            updatingGUI = true;

            // we clear and re-create GenericFilterEditors
            jPanelGenericFilters.removeAll();
            genericFilterEditorList.forEach(GenericFilterEditor::dispose);
            genericFilterEditorList.clear();

            // we clear and recreate column name choices
            columnChoices.clear();

            final SubsetDefinition subsetDefinitionCopy = OCM.getCurrentSubsetDefinition();

            if (subsetDefinitionCopy != null) {
                final SelectorResult selectorResult = subsetDefinitionCopy.getSelectorResult();

                for (GenericFilter genericFilter : subsetDefinitionCopy.getGenericFilters()) {
                    addGenericFilterEditor(genericFilter);
                }

                // updating column choices from SelectorResult
                for (String specialName : Selector.SPECIAL_COLUMN_NAMES) {
                    columnChoices.add(specialName);
                }
                for (String columnName : getDistinctNumericalColumnNames(selectorResult)) {
                    columnChoices.add(columnName);
                }
                if (jComboBoxColumnName.getSelectedIndex() == -1) {
                    jComboBoxColumnName.setSelectedIndex(0);
                }

                // CLI args
                final String cliArgs
                             = (selectorResult == null)
                                ? ""
                                : OIFitsProcessor.generateCLIargs(selectorResult.getSelector());
                jTextAreaCLI.setText(cliArgs);
            }

            revalidate();
        } finally {
            updatingGUI = false;
        }
    }

    /** Adds a GenericFilterEditor to the Panel, along with a delete button */
    private void addGenericFilterEditor(final GenericFilter genericFilter) {
        final JPanel panel = new JPanel(new GridBagLayout());
        final GridBagConstraints gridBagConstraints = new GridBagConstraints(); // gridBagConstraints

        final GenericFilterEditor newGenericFilterEditor = new GenericFilterEditor();
        newGenericFilterEditor.addChangeListener(this);
        newGenericFilterEditor.setGenericFilter(genericFilter);
        genericFilterEditorList.add(newGenericFilterEditor);

        gridBagConstraints.gridx = 0;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.9;
        panel.add(newGenericFilterEditor, gridBagConstraints);

        final JButton delButton = new JButton();
        delButton.setIcon(ResourceImage.LIST_DEL.icon());
        delButton.addActionListener(this);
        delButton.setMargin(new Insets(0, 0, 0, 0));
        gridBagConstraints.gridx = 1;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.weightx = 0;
        gridBagConstraints.insets = new Insets(0, 4, 0, 4);
        panel.add(delButton, gridBagConstraints);

        jPanelGenericFilters.add(panel, 0);
    }

    /** Handler for the Add button, adds a new generic filter editor */
    private void handlerAddGenericFilter() {
        if (!updatingGUI) {
            final GenericFilter newGenericFilter = new GenericFilter();
            newGenericFilter.setEnabled(true);

            String columnName = (String) jComboBoxColumnName.getSelectedItem();
            if (columnName == null) {
                columnName = Selector.FILTER_EFFWAVE;
            }
            newGenericFilter.setColumnName(columnName);

            final DataType dataType = Selector.isRangeFilter(columnName) ? DataType.NUMERIC : DataType.STRING;
            newGenericFilter.setDataType(dataType);

            switch (dataType) {
                case NUMERIC:
                    final fr.jmmc.oitools.model.range.Range oitoolsRange
                                                            = OCM.getOIFitsCollection().getColumnRange(columnName);
                    final Range range = new Range();
                    range.setMin(Double.isFinite(oitoolsRange.getMin()) ? oitoolsRange.getMin() : Double.NaN);
                    range.setMax(Double.isFinite(oitoolsRange.getMax()) ? oitoolsRange.getMax() : Double.NaN);
                    newGenericFilter.getAcceptedRanges().add(range);
                    break;
                case STRING:
                    final List<String> initValues = OCM.getOIFitsCollection().getDistinctValues(columnName);
                    if (initValues != null) {
                        newGenericFilter.getAcceptedValues().addAll(initValues);
                    }
                    break;
                default:
            }
            addGenericFilterEditor(newGenericFilter);

            revalidate();
            updateModel();
        }
    }

    /** Handler for the Del button. removes the generic filter editor associated to the button */
    private void handlerDelGenericFilter(final JButton delButton) {
        if (!updatingGUI) {
            try {
                /* retrieve the panel containing the actioned button and the generic filter editor to delete */
                delButton.removeActionListener(this);
                JPanel panel = (JPanel) delButton.getParent();
                GenericFilterEditor genericFilterEditorToDel = (GenericFilterEditor) panel.getComponent(0);
                genericFilterEditorList.remove(genericFilterEditorToDel);
                jPanelGenericFilters.remove(panel);

                revalidate();
                repaint();
                updateModel();
            } catch (ClassCastException e) {
                logger.error("Cannot find GenericFilterEditor panel to remove.");
            }
        }
    }

    /** Listener on actions on the del buttons.
     *
     * @param evt Event, the del button is the source
     */
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        if (evt.getSource() instanceof JButton) {
            handlerDelGenericFilter((JButton) evt.getSource());
        }
    }

    /** Listener on changes on GenericFilterEditors
     *
     * @param ce Event
     */
    @Override
    public void stateChanged(ChangeEvent ce) {
        if (!updatingGUI) {
            updateModel();
        }
    }

    /*
     * OIFitsCollectionManagerEventListener implementation
     */
    /**
     * Return the optional subject id i.e. related object id that this listener accepts
     *
     * @param type event type
     * @return subject id (null means accept any event) or DISCARDED_SUBJECT_ID to discard event
     */
    @Override
    public String getSubjectId(final OIFitsCollectionManagerEventType type) {
        // accept all
        return null;
    }

    @Override
    public void onProcess(OIFitsCollectionManagerEvent event) {
        logger.debug("onProcess begin {}", event);

        switch (event.getType()) {
            case SUBSET_CHANGED:
                updateGUI();
                break;
            default:
        }
        logger.debug("onProcess done {}", event);
    }

    /**
     * Return the set of distinct columns available in tables of the given SelectorResult.
     *
     * @param selectorResult Selector result from plot's subset definition
     * @return a Set of Strings with every distinct column names
     */
    private static Set<String> getDistinctNumericalColumnNames(final SelectorResult selectorResult) {
        return SelectorResult.getDataModel(selectorResult).getNumericalColumnNames();
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The
     * content of this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jPanelToolbar = new javax.swing.JPanel();
        jButtonAddGenericFilter = new javax.swing.JButton();
        jComboBoxColumnName = new javax.swing.JComboBox<>();
        jScrollPaneFilters = new javax.swing.JScrollPane();
        jPanelGenericFilters = new javax.swing.JPanel();
        jScrollPaneCLI = new javax.swing.JScrollPane();
        jTextAreaCLI = new javax.swing.JTextArea();

        setBorder(javax.swing.BorderFactory.createTitledBorder("Generic Filters"));
        setLayout(new java.awt.GridBagLayout());

        jPanelToolbar.setLayout(new java.awt.GridBagLayout());

        jButtonAddGenericFilter.setIcon(fr.jmmc.jmcs.gui.util.ResourceImage.LIST_ADD.icon());
        jButtonAddGenericFilter.setMargin(new java.awt.Insets(0, 0, 0, 0));
        jButtonAddGenericFilter.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonAddGenericFilterActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        jPanelToolbar.add(jButtonAddGenericFilter, gridBagConstraints);

        jComboBoxColumnName.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "COLUMN_NAME" }));
        jComboBoxColumnName.setPrototypeDisplayValue("XXXX");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.8;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        jPanelToolbar.add(jComboBoxColumnName, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.9;
        add(jPanelToolbar, gridBagConstraints);

        jPanelGenericFilters.setLayout(new javax.swing.BoxLayout(jPanelGenericFilters, javax.swing.BoxLayout.Y_AXIS));
        jScrollPaneFilters.setViewportView(jPanelGenericFilters);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.9;
        gridBagConstraints.weighty = 0.9;
        add(jScrollPaneFilters, gridBagConstraints);

        jScrollPaneCLI.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        jTextAreaCLI.setEditable(false);
        jTextAreaCLI.setColumns(20);
        jTextAreaCLI.setLineWrap(true);
        jTextAreaCLI.setRows(4);
        jTextAreaCLI.setTabSize(4);
        jTextAreaCLI.setToolTipText("OITools CLI arguments equivalent to current filters");
        jTextAreaCLI.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        jScrollPaneCLI.setViewportView(jTextAreaCLI);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.9;
        gridBagConstraints.weighty = 0.1;
        add(jScrollPaneCLI, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

    private void jButtonAddGenericFilterActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonAddGenericFilterActionPerformed
        handlerAddGenericFilter();
    }//GEN-LAST:event_jButtonAddGenericFilterActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButtonAddGenericFilter;
    private javax.swing.JComboBox<String> jComboBoxColumnName;
    private javax.swing.JPanel jPanelGenericFilters;
    private javax.swing.JPanel jPanelToolbar;
    private javax.swing.JScrollPane jScrollPaneCLI;
    private javax.swing.JScrollPane jScrollPaneFilters;
    private javax.swing.JTextArea jTextAreaCLI;
    // End of variables declaration//GEN-END:variables

}
