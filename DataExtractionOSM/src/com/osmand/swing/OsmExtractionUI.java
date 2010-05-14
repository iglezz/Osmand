package com.osmand.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellEditor;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.SAXException;

import com.osmand.Algoritms;
import com.osmand.DefaultLauncherConstants;
import com.osmand.ExceptionHandler;
import com.osmand.IMapLocationListener;
import com.osmand.data.Amenity;
import com.osmand.data.City;
import com.osmand.data.Region;
import com.osmand.data.Street;
import com.osmand.data.Amenity.AmenityType;
import com.osmand.data.City.CityType;
import com.osmand.data.preparation.DataExtraction;
import com.osmand.data.preparation.DataIndexBuilder;
import com.osmand.osm.Entity;
import com.osmand.osm.LatLon;
import com.osmand.osm.MapUtils;
import com.osmand.osm.Node;
import com.osmand.osm.OSMSettings.OSMTagKey;

public class OsmExtractionUI implements IMapLocationListener {

	private static final Log log = LogFactory.getLog(OsmExtractionUI.class);  
	
	public static void main(String[] args) {
        OsmExtractionUI ui = new OsmExtractionUI(null);
        ui.runUI();
	}
	
	protected City selectedCity;
	private MapPanel mapPanel = new MapPanel(new File(DefaultLauncherConstants.pathToDirWithTiles));
	
	private DataExtractionTreeNode amenitiesTree;
	private JTree treePlaces;
	private JList searchList;
	private JTextField searchTextField;
	
	private JFrame frame;
	private JLabel statusBarLabel;
	
	private Region region;
	private File workingDir;
	private JButton generateDataButton;
	private JCheckBox buildPoiIndex;
	private JCheckBox buildAddressIndex;
	private TreeModelListener treeModelListener;
	
	
	public OsmExtractionUI(final Region r){
		this.region = r;
		workingDir = new File(DefaultLauncherConstants.pathToWorkingDir);
		createUI();
		setRegion(r, "Region");
	}

	
	public void setRegion(Region region, String name){
		if (this.region == region) {
			return;
		}
		this.region = region;
		DefaultMutableTreeNode root = new DataExtractionTreeNode(name, region);
		amenitiesTree = new DataExtractionTreeNode("Closest amenities", region);
		amenitiesTree.add(new DataExtractionTreeNode("First 15", region));
		for(AmenityType type : AmenityType.values()){
			amenitiesTree.add(new DataExtractionTreeNode(Algoritms.capitalizeFirstLetterAndLowercase(type.toString()), type));
		}
		root.add(amenitiesTree);

		if (region != null) {
			for (CityType t : CityType.values()) {
				DefaultMutableTreeNode cityTree = new DataExtractionTreeNode(Algoritms.capitalizeFirstLetterAndLowercase(t.toString()), t);
				root.add(cityTree);
				for (City ct : region.getCitiesByType(t)) {
					DefaultMutableTreeNode cityNodeTree = new DataExtractionTreeNode(ct.getName(), ct);
					cityTree.add(cityNodeTree);

					for (Street str : ct.getStreets()) {
						DefaultMutableTreeNode strTree = new DataExtractionTreeNode(str.getName(), str);
						cityNodeTree.add(strTree);
						for (Entity e : str.getBuildings()) {
							DefaultMutableTreeNode building = new DataExtractionTreeNode(e.getTag(OSMTagKey.ADDR_HOUSE_NUMBER), e);
							strTree.add(building);

						}
					}
				}
			}
		}
		
		// amenities could be displayed as dots
//		DataTileManager<LatLon> amenitiesManager = new DataTileManager<LatLon>();
//		if (region != null) {
//			for (Amenity a : region.getAmenityManager().getAllObjects()) {
//				amenitiesManager.registerObject(a.getNode().getLatitude(), a.getNode().getLongitude(), a.getNode().getLatLon());
//			}
//		}
//	    mapPanel.setPoints(amenitiesManager);
	    if (searchList != null) {
			updateListCities(region, searchTextField.getText(), searchList);
		}
		mapPanel.repaint();
		DefaultTreeModel newModel = new DefaultTreeModel(root, false);
		newModel.addTreeModelListener(treeModelListener);
		treePlaces.setModel(newModel);
		updateButtonsBar();
	}
        
	
	
	public void createUI(){
		frame = new JFrame("OsmAnd Map Creator");
	    try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			log.error("Can't set look and feel", e);
		}
		
		
	    frame.addWindowListener(new ExitListener());
	    Container content = frame.getContentPane();
	    frame.setFocusable(true);
	    
	    statusBarLabel = new JLabel();
	    content.add(statusBarLabel, BorderLayout.SOUTH);
	    statusBarLabel.setText(workingDir == null ? "<working directory unspecified>" : "Working directory : " + workingDir.getAbsolutePath());
	    
	   
	    	    
	    JSplitPane panelForTreeAndMap = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(createTree(content)), mapPanel);
	    panelForTreeAndMap.setResizeWeight(0.2);
	    mapPanel.setFocusable(true);
	    mapPanel.addMapLocationListener(this);
	    
	    
	    createButtonsBar(content);
//	    createCitySearchPanel(content);
	    if(searchList != null){
	    	JSplitPane pane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(searchList), panelForTreeAndMap);
	    	pane.setResizeWeight(0.2);
	    	content.add(pane, BorderLayout.CENTER);
	    } else {
	    	content.add(panelForTreeAndMap, BorderLayout.CENTER);
	    }
	   
	    JMenuBar bar = new JMenuBar();
	    fillMenuWithActions(bar);
	    
	    frame.setJMenuBar(bar);
	}
	
	public JTree createTree(Container content) {
		treePlaces = new JTree();
		treePlaces.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Region"), false));
		treePlaces.setEditable(true);
		treePlaces.setCellEditor(new RegionCellEditor(treePlaces, (DefaultTreeCellRenderer) treePlaces.getCellRenderer()));
		treePlaces.addTreeSelectionListener(new TreeSelectionListener() {
			@Override
			public void valueChanged(TreeSelectionEvent e) {
				if (e.getPath() != null) {
					if (e.getPath().getLastPathComponent() instanceof DataExtractionTreeNode) {
						Object o = ((DataExtractionTreeNode) e.getPath().getLastPathComponent()).getModelObject();

						if (o instanceof City) {
							City c = (City) o;
							mapPanel.setLatLon(c.getNode().getLatitude(), c.getNode().getLongitude());
							mapPanel.requestFocus();
						} else if (o instanceof Street) {
							Street s = (Street) o;
							LatLon center = MapUtils.getWeightCenterForNodes(s.getWayNodes());
							if(center != null){
								mapPanel.setLatLon(center.getLatitude(), center.getLongitude());
								mapPanel.requestFocus();
							}
						} else if (o instanceof Amenity) {
							Amenity c = (Amenity) o;
							mapPanel.setLatLon(c.getNode().getLatitude(), c.getNode().getLongitude());
							mapPanel.requestFocus();
						} else if (o instanceof Entity) {
							Entity c = (Entity) o;
							if (c instanceof Node) {
								mapPanel.setLatLon(((Node) c).getLatitude(), ((Node) c).getLongitude());
								mapPanel.requestFocus();
							} else {
								DataExtractionTreeNode n = (DataExtractionTreeNode) e.getPath().getPathComponent(
										e.getPath().getPathCount() - 2);
								if (n.getModelObject() instanceof Street) {
									Street str = (Street) n.getModelObject();
									LatLon l = str.getLocationBuilding(c);
									mapPanel.setLatLon(l.getLatitude(), l.getLongitude());
									mapPanel.requestFocus();
								}
							}
						}
					}
				}

			}
		});
		
		treeModelListener = new TreeModelListener() {
		    public void treeNodesChanged(TreeModelEvent e) {
		        DefaultMutableTreeNode node;
		        node = (DefaultMutableTreeNode)
		                 (e.getTreePath().getLastPathComponent());
		        if(node instanceof DataExtractionTreeNode && ((DataExtractionTreeNode) node).getModelObject() instanceof Region){
		        	Region r = (Region) ((DataExtractionTreeNode) node).getModelObject();
		        	r.setName(node.getUserObject().toString());
		        }
		    }
		    public void treeNodesInserted(TreeModelEvent e) {
		    }
		    public void treeNodesRemoved(TreeModelEvent e) {
		    }
		    public void treeStructureChanged(TreeModelEvent e) {
		    }
		};
		treePlaces.getModel().addTreeModelListener(treeModelListener);
		return treePlaces;
	}
	
	protected void updateButtonsBar() {
		generateDataButton.setEnabled(workingDir != null && region != null);
		buildAddressIndex.setEnabled(generateDataButton.isEnabled() && region.getCitiesCount(null) > 0);
		buildPoiIndex.setEnabled(generateDataButton.isEnabled() && !region.getAmenityManager().isEmpty());
	}
	
	public void createButtonsBar(Container content){
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		content.add(panel, BorderLayout.NORTH);
		
		generateDataButton = new JButton();
		generateDataButton.setText("Generate data ");
		generateDataButton.setToolTipText("Data with selected preferences will be generated in working directory." +
				" 	The index files will be named as region in tree. All existing data will be overwritten.");
		panel.add(generateDataButton);
		
		generateDataButton.addActionListener(new ActionListener(){

			@Override
			public void actionPerformed(ActionEvent e) {
				DataIndexBuilder builder = new DataIndexBuilder(workingDir, region);
				StringBuilder msg = new StringBuilder();
				try {
					msg.append("Indices checked for ").append(region.getName());
					if(buildPoiIndex.isEnabled()){
						builder.buildPOI();
						msg.append(", POI index ").append("successfully created");
					}
					msg.append(".");
				    JOptionPane pane = new JOptionPane(msg);
				    JDialog dialog = pane.createDialog(frame, "Generation data");
				    dialog.setVisible(true);
				} catch (XMLStreamException e1) {
					ExceptionHandler.handle(e1);
				} catch (IOException e1) {
					ExceptionHandler.handle(e1);
				}
				
			}
			
		});
		
		buildPoiIndex = new JCheckBox();
		buildPoiIndex.setText("Build POI index");
		panel.add(buildPoiIndex);
		buildPoiIndex.setSelected(true);
		
		buildAddressIndex = new JCheckBox();
		buildAddressIndex.setText("Build Address index");
		panel.add(buildAddressIndex);
		buildAddressIndex.setSelected(true);
		
		updateButtonsBar();
	}

	public void createCitySearchPanel(Container content){
		JPanel panel = new JPanel(new BorderLayout());
	    searchTextField = new JTextField();
	    final JButton button = new JButton();
	    button.setText("Set town");

	    
	    panel.add(searchTextField, BorderLayout.CENTER);
	    panel.add(button, BorderLayout.WEST);
	    
	    content.add(panel, BorderLayout.NORTH);
	    
		
		searchList = new JList();
	    searchList.setCellRenderer(new DefaultListCellRenderer(){
			private static final long serialVersionUID = 4661949460526837891L;

			@Override
	    	public Component getListCellRendererComponent(JList list,
	    			Object value, int index, boolean isSelected,
	    			boolean cellHasFocus) {
	    		super.getListCellRendererComponent(list, value, index, isSelected,
	    				cellHasFocus);
	    		if(value instanceof City){
	    			setText(((City)value).getName());
	    		}
	    		return this;
	    	}
	    });

	    
	    updateListCities(region, searchTextField.getText(), searchList);
	    searchTextField.getDocument().addUndoableEditListener(new UndoableEditListener(){
			@Override
			public void undoableEditHappened(UndoableEditEvent e) {
	    		updateListCities(region, searchTextField.getText(), searchList);
			}
	    });
	    
	    button.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				selectedCity = (City)searchList.getSelectedValue();
			}
	    });

	    searchList.addListSelectionListener(new ListSelectionListener(){
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if(searchList.getSelectedValue() != null){
					Node node = ((City)searchList.getSelectedValue()).getNode();
					String text = "Lat : " + node.getLatitude() + " Lon " + node.getLongitude();
					if(selectedCity != null){
						text += " distance " + MapUtils.getDistance(selectedCity.getNode(), node);
					}
					mapPanel.setLatLon(node.getLatitude(), node.getLongitude());
				}
			}
	    });
	    
	    
	}
	
	public void runUI(){
		frame.setSize(1024, 768);
	    frame.setVisible(true);
	}
	
	public void fillMenuWithActions(JMenuBar bar){
		JMenu menu = new JMenu("File");
		bar.add(menu);
		JMenuItem loadFile = new JMenuItem("Load osm file...");
		menu.add(loadFile);
		JMenuItem specifyWorkingDir = new JMenuItem("Specify working directory...");
		menu.add(specifyWorkingDir);
		menu.addSeparator();
		JMenuItem exitMenu= new JMenuItem("Exit");
		menu.add(exitMenu);
		
		bar.add(MapPanel.getMenuToChooseSource(mapPanel));
		
		exitMenu.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.setVisible(false);
			}
		});
		specifyWorkingDir.addActionListener(new ActionListener(){

			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser fc = new JFileChooser();
		        fc.setDialogTitle("Choose working directory");
		        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		        if(workingDir != null){
		        	fc.setCurrentDirectory(workingDir);
		        }
		        if(fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION && fc.getSelectedFile() != null && 
		        		fc.getSelectedFile().isDirectory()){
		        	workingDir = fc.getSelectedFile();
		        	statusBarLabel.setText("Working directory : " + fc.getSelectedFile().getAbsolutePath());
		        	updateButtonsBar();
		        }
			}
			
		});
		
		loadFile.addActionListener(new ActionListener(){

			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser fc = new JFileChooser();
		        fc.setDialogTitle("Choose osm file");
		        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
		        fc.setAcceptAllFileFilterUsed(true);
		        fc.setCurrentDirectory(new File(DefaultLauncherConstants.pathToTestDataDir));
		        //System.out.println("opening fc for extension " + extension);
		        fc.setFileFilter(new FileFilter(){

					@Override
					public boolean accept(File f) {
						return f.isDirectory() || f.getName().endsWith(".bz2") || f.getName().endsWith(".osm");
					}

					@Override
					public String getDescription() {
						return "Osm Files (*.bz2, *.osm)";
					}
		        });

		        int answer = fc.showOpenDialog(frame) ;
		        if (answer == JFileChooser.APPROVE_OPTION && fc.getSelectedFile() != null){
		        	loadCountry(fc.getSelectedFile());
		        }
			}
			
		});
	}
	
	public void loadCountry(final File f){
		try {
    		final ProgressDialog dlg = new ProgressDialog(frame, "Loading osm file");
    		dlg.setRunnable(new Runnable(){

				@Override
				public void run() {
					Region res;
					try {
						res = new DataExtraction().readCountry(f.getAbsolutePath(), dlg);
					} catch (IOException e) {
						throw new IllegalArgumentException(e);
					} catch (SAXException e) {
						throw new IllegalStateException(e);
					}
					dlg.setResult(res);
				}
    		});
			Region region = (Region) dlg.run();
			if(region != null){
				int i = f.getName().indexOf('.');
				if(region.getName().isEmpty()){
					region.setName(Algoritms.capitalizeFirstLetterAndLowercase(f.getName().substring(0, i)));
				}
				setRegion(region, region.getName());
				frame.setTitle("OsmAnd Map Creator - " + f.getName());
			} else {
				//frame.setTitle("OsmAnd Map Creator");
			}
		} catch (InterruptedException e1) {
			log.error("Interrupted", e1); 
		} catch (InvocationTargetException e1) {
			log.error("Exception during operation", e1.getCause());
		}
	}
	
	@Override
	public void locationChanged(final double newLatitude, final double newLongitude, Object source){
		if (amenitiesTree != null) {
			Region reg = (Region) amenitiesTree.getModelObject();
			List<Amenity> closestAmenities = reg.getClosestAmenities(newLatitude, newLongitude);
			Collections.sort(closestAmenities, new Comparator<Amenity>() {
				@Override
				public int compare(Amenity o1, Amenity o2) {
					return Double.compare(MapUtils.getDistance(o1.getNode(), newLatitude, newLongitude), MapUtils.getDistance(o2.getNode(),
							newLatitude, newLongitude));
				}
			});

			Map<AmenityType, List<Amenity>> filter = new TreeMap<AmenityType, List<Amenity>>();
			for (Amenity n : closestAmenities) {
				AmenityType type = n.getType();
				if (!filter.containsKey(type)) {
					filter.put(type, new ArrayList<Amenity>());
				}
				filter.get(type).add(n);
			}
			
			
			for (int i = 1; i < amenitiesTree.getChildCount(); i++) {
				AmenityType type = (AmenityType) ((DataExtractionTreeNode) amenitiesTree.getChildAt(i)).getModelObject();
				((DefaultMutableTreeNode) amenitiesTree.getChildAt(i)).removeAllChildren();
				if (filter.get(type) != null) {
					for (Amenity n : filter.get(type)) {
						int dist = (int) (MapUtils.getDistance(n.getNode(), newLatitude, newLongitude));
						String str = n.getStringWithoutType() + " [" + dist + " m ]";
						DataExtractionTreeNode node = new DataExtractionTreeNode(str, n);
						((DefaultMutableTreeNode) amenitiesTree.getChildAt(i)).add(node);
					}
				}
				((DefaultTreeModel)treePlaces.getModel()).nodeStructureChanged(amenitiesTree.getChildAt(i));
			}
			((DefaultMutableTreeNode) amenitiesTree.getChildAt(0)).removeAllChildren();

			for (int i = 0; i < 15 && i < closestAmenities.size(); i++) {
				Amenity n = closestAmenities.get(i);
				int dist = (int) (MapUtils.getDistance(n.getNode(), newLatitude, newLongitude));
				String str = n.getSimpleFormat() + " [" + dist + " m ]";
				((DefaultMutableTreeNode) amenitiesTree.getChildAt(0)).add(new DataExtractionTreeNode(str, n));
				((DefaultTreeModel)treePlaces.getModel()).nodeStructureChanged(amenitiesTree.getChildAt(0));
			}

			
		}
	}
	
	public void updateListCities(Region r, String text, JList jList) {
		Collection<City> city;
		if (r == null) {
			city = Collections.emptyList();
		} else {
			city = r.getSuggestedCities(text, 100);
		}
		City[] names = new City[city.size()];
		int i = 0;
		for (City c : city) {
			names[i++] = c;
		}
		jList.setListData(names);
	}
	
	
	public static class DataExtractionTreeNode extends DefaultMutableTreeNode {
		private static final long serialVersionUID = 1L;
		private final Object modelObject;

		public DataExtractionTreeNode(String name, Object modelObject){
			super(name);
			this.modelObject = modelObject;
		}
		
		public Object getModelObject(){
			return modelObject;
		}
		
	}
	
	public static class RegionCellEditor extends DefaultTreeCellEditor {

		public RegionCellEditor(JTree tree, DefaultTreeCellRenderer renderer) {
			super(tree, renderer);
		}

		public RegionCellEditor(JTree tree, DefaultTreeCellRenderer renderer, TreeCellEditor editor) {
			super(tree, renderer, editor);
		}

		public boolean isCellEditable(EventObject event) {
			boolean returnValue = super.isCellEditable(event);
			if (returnValue) {
				Object node = tree.getLastSelectedPathComponent();
				if (node instanceof DataExtractionTreeNode) {
					DataExtractionTreeNode treeNode = (DataExtractionTreeNode) node;
					if (treeNode.getModelObject() instanceof Region) {
						return true;
					}
				}
			}
			return returnValue;
		}
		
	}
	public static class ExitListener extends WindowAdapter {
		public void windowClosing(WindowEvent event) {
			System.exit(0);
		}
	}

}
