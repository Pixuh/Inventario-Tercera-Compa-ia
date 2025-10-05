import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class EppFrame extends JDialog {

    private final int idProducto;
    private final EppService eppService;

    private JTable tblItems;
    private DefaultTableModel itemsModel;

    private JTable tblMov;
    private DefaultTableModel movModel;

    private JLabel lblProducto;

    public EppFrame(Frame owner, int idProducto, EppService eppService) {
        super(owner, "Gestión EPP", true);
        this.idProducto = idProducto;
        this.eppService = eppService;

        setSize(960, 600);
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);

        loadItems();
    }

    private JComponent buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        lblProducto = new JLabel("Producto ID: " + idProducto);
        lblProducto.setBorder(BorderFactory.createEmptyBorder(8,12,8,12));
        p.add(lblProducto, BorderLayout.WEST);
        return p;
    }

    private JComponent buildCenter() {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setResizeWeight(0.65);

        // Tabla de ítems EPP
        itemsModel = new DefaultTableModel(
                new Object[]{"ID EPP", "Código", "Serie", "Talla", "Bodega", "Subbodega", "Estado"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tblItems = new JTable(itemsModel);
        tblItems.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblItems.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = tblItems.getSelectedRow();
                if (row >= 0) {
                    int idEpp = (int) itemsModel.getValueAt(row, 0);
                    loadMovimientos(idEpp);
                } else {
                    clearMovimientos();
                }
            }
        });

        // Historial de movimientos
        movModel = new DefaultTableModel(
                new Object[]{"Fecha", "Desde Bodega", "Desde Subb.", "Hacia Bodega", "Hacia Subb.", "Motivo"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tblMov = new JTable(movModel);

        split.setTopComponent(new JScrollPane(tblItems));
        split.setBottomComponent(new JScrollPane(tblMov));
        return split;
    }

    private void loadItems() {
        try {
            itemsModel.setRowCount(0);
            // Usa el servicio/DAO para traer los ítems de este producto
            List<EppService.ItemEppDTO> items = eppService.listarItemsPorProducto(idProducto);
            for (EppService.ItemEppDTO it : items) {
                itemsModel.addRow(new Object[]{
                        it.idEpp,
                        it.codigo,
                        it.serie,
                        it.talla,
                        it.bodega,
                        it.subbodega,
                        it.estado
                });
            }
            if (itemsModel.getRowCount() > 0) {
                tblItems.setRowSelectionInterval(0, 0);
            } else {
                clearMovimientos();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudieron cargar los ítems EPP: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadMovimientos(int idEpp) {
        try {
            movModel.setRowCount(0);
            List<EppService.MovDTO> movs = eppService.listarMovimientos(idEpp);
            for (EppService.MovDTO m : movs) {
                movModel.addRow(new Object[]{
                        m.fecha,
                        m.desdeBodega,
                        m.desdeSubbodega,
                        m.haciaBodega,
                        m.haciaSubbodega,
                        m.motivo
                });
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo cargar el historial: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearMovimientos() {
        movModel.setRowCount(0);
    }
}
