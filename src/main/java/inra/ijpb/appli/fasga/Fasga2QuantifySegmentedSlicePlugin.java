/**
 * 
 */
package inra.ijpb.appli.fasga;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import inra.ijpb.binary.ConnectedComponents;
import inra.ijpb.label.LabelImages;

/**
 * Plugin for quantifying the colorimetry and morphology in various regions of a
 * color image.
 * 
 * @author David Legland
 *
 */
public class Fasga2QuantifySegmentedSlicePlugin implements PlugIn
{
	/** Apparently, it's better to store flags in plugin */
	private int flags = PlugInFilter.DOES_ALL;
	
	ImageProcessor refImage = null;
	ImageProcessor labelImage = null;
	double resol = 0;
	String refImageName = null;
	
	/**
	 * Keep an instance of the results table, so that it is possible to re-use
	 * it on another image. 
	 */
	static ResultsTable fasgaResults = null;
	
	@Override
	public void run(String arg0)
	{
		int flag = showDialog();
		if (flag == 0) 
			return;
		
		ResultsTable table = quantifyRegions(refImage, labelImage, resol);
		table.setLabel(refImageName, table.getCounter() - 1);
		
		table.show("Quantif. Fasga");
	}

	/**
	 * Displays dialog for choosing parameter, using same signature as for the
	 * PlugInFilter interface. Waits for OK or Cancel, and returns the state.
	 */
	public int showDialog()
	{
		System.out.println("show dialog");
		
		// Open a dialog to choose:
		// - a reference image (grayscale, binary, or color)
		// - a binary image (coded as uint8)
		// - a target color
		int[] indices = WindowManager.getIDList();
		if (indices==null) 
		{
			IJ.error("No image", "Need at least one image to work");
			return PlugInFilter.DONE;
		}

		// create the list of image names
		String[] imageNames = new String[indices.length];
		for (int i = 0; i < indices.length; i++) 
		{
			imageNames[i] = WindowManager.getImage(indices[i]).getTitle();
		}
		
		// name of selected image
		String selectedImageName = IJ.getImage().getTitle();
		
		// create the dialog
    	GenericDialog gd = new GenericDialog("Fasga Quantify Regions");
    	
		gd.addChoice("Reference Image:", imageNames, selectedImageName);
		gd.addChoice("Label Image:", imageNames, selectedImageName);
		
		gd.addNumericField("Resolution", 1, 2);

		gd.showDialog();
		if (gd.wasCanceled())
			return PlugInFilter.DONE;

		parseDialogParameters(gd);

		// clean up an return 
		gd.dispose();
		return flags;
	}

	private void parseDialogParameters(GenericDialog gd) 
	{
		// Extract parameters
		int refImageIndex = (int) gd.getNextChoiceIndex();
		int labelImageIndex = (int) gd.getNextChoiceIndex();
		this.resol = gd.getNextNumber();
		
		// get selected images
		ImagePlus refPlus = WindowManager.getImage(refImageIndex + 1);
		this.refImage = refPlus.getProcessor();
		this.refImageName = refPlus.getShortTitle();
		this.labelImage = WindowManager.getImage(labelImageIndex + 1).getProcessor();
    }


	public static final ResultsTable quantifyRegions(ImageProcessor refImage,
			ImageProcessor labelImage, double resol)
	{
		// extract image size
		int width = labelImage.getWidth();
		int height = labelImage.getHeight();
		
		// First, compute number of pixels in each region, also including stem
		int nPixelStem = 0;
		int nPixelRind = 0;
		int nPixelBundles = 0;
		int nPixelRed = 0;
		int nPixelBlue = 0;
		for (int y = 0; y < height; y++) 
		{
			for (int x = 0; x < width; x++)
			{
				int label = labelImage.get(x, y); 
				if (label == 0)
					continue;
				
				nPixelStem++;
				
				switch (label)
				{
				case 1: nPixelRed++; break;
				case 2: nPixelBlue++; break;
				case 3: nPixelRind++; break;
				case 4: nPixelBundles++; break;
				default:
					System.out.println("Unknown label: " + label);
				}
			}
		}
		
		// Extract image of bundles for counting them
		ImageProcessor bundlesImage = new ByteProcessor(width, height);
		for (int y = 0; y < height; y++) 
		{
			for (int x = 0; x < width; x++)
			{
				if (labelImage.get(x, y) == 4)
					bundlesImage.set(x, y, 255);
			}
		}
		
		
		// Analyze bundles -> number of bundles
		ImageProcessor bunLabels = ConnectedComponents.computeLabels(bundlesImage, 4, 16);
		int[] labels = LabelImages.findAllLabels(bunLabels);
		int bundlesNumber = labels.length;

		// Compute fraction of each region
		double bundlesFraction = (double) nPixelBundles / (double) nPixelStem; 
		double rindFraction = (double) nPixelRind / (double) nPixelStem; 
		double redFraction = (double) nPixelRed / (double) nPixelStem; 
		double blueFraction = (double) nPixelBlue / (double) nPixelStem; 

		// Calcule la couleur moyenne dans chacune des regions
		ResultsTable rgbTable = DistanceProfile.colorByRegion(
				(ColorProcessor) refImage, labelImage);
		
		// Creates the new table
		if (fasgaResults == null) 
		{
			fasgaResults = new ResultsTable();
		}
		ResultsTable table = fasgaResults;
		table.incrementCounter();
		
		// Add area fractions of each region
		if (resol == 0) resol = 1;
		double stemArea = nPixelStem * resol * resol;
		table.addValue("StemArea", stemArea);
		table.addValue("LignifiedFraction", redFraction);
		table.addValue("NonLignifiedFraction", blueFraction);
		table.addValue("RindFraction", rindFraction);
		table.addValue("BundleFraction", bundlesFraction);
		table.addValue("BundleNumber", bundlesNumber);
		table.addValue("BundleIntensity", bundlesNumber / stemArea);

		String[] regionLabels = new String[]{"Lignified", "NonLignified", "Rind", "Bundle"};
		String[] channelNames = new String[]{"Red", "Green", "Blue"};
		for (int r = 0; r < 4; r++)
		{
			for (int c = 0; c < 3; c++)
			{
				String colName = regionLabels[r] + "Mean" + channelNames[c];
				table.addValue(colName, rgbTable.getValueAsDouble(c, r));
			}
				
		}
		return table;
	}
}
