/**
 * 
 */
package inra.ijpb.appli.fasga;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.PlugInFilter;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import inra.ijpb.binary.BinaryImages;

import java.awt.Color;

/**
 * Compute color profiles from a color image and a binary image of the stem.
 * 
 * @author David Legland
 *
 */
public class Fasga2ColorProfilesPlugin implements PlugIn
{
	/** Apparently, it's better to store flags in plugin */
	private int flags = PlugInFilter.DOES_ALL;
	
	ImageProcessor colorImage = null;
	ImageProcessor stemImage = null;
	int pointNumber = 100;
		
	@Override
	public void run(String arg0)
	{
		int flag = showDialog();
		if (flag == PlugInFilter.DONE) 
			return;
		
		ResultsTable table = computeColorProfiles(colorImage, stemImage, pointNumber);
		if (table != null) 
		{
			table.show("Color Profiles");
		}
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
    	GenericDialog gd = new GenericDialog("Fasga Color Profiles");
    	
		gd.addChoice("Reference Image:", imageNames, selectedImageName);
		gd.addChoice("Stem Image:", imageNames, selectedImageName);
		
		gd.addNumericField("Number of points:", 100, 0);

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
		this.pointNumber = (int) gd.getNextNumber();
		
		// get selected images
		ImagePlus refPlus = WindowManager.getImage(refImageIndex + 1);
		this.colorImage = refPlus.getProcessor();
		this.stemImage = WindowManager.getImage(labelImageIndex + 1).getProcessor();
    }


	public static final ResultsTable computeColorProfiles(ImageProcessor refImage,
			ImageProcessor stemImage, int regionNumber)
	{
		IJ.log("Compute color profiles");
		
		// Check that label image has adequate type
		if (stemImage.getBitDepth() != 8)
		{
			IJ.error("Stem Image must have 8-bits depth");
			return null;
		}
		
		ImageProcessor distMap = BinaryImages.distanceMap(stemImage);
		
		ImageProcessor regions = DistanceProfile.distanceMapToClasses(distMap, 
				regionNumber); 
		
		// Compute average color in each region
		ResultsTable rgbTable = DistanceProfile.colorByRegion(
				(ColorProcessor) refImage, regions);
		
//		// get results table, or create one if necessary
//		ResultsTable table = getResultsTable();
//		table.incrementCounter();
		
		plotColorProfiles(rgbTable);
		
		IJ.log("  (color profiles done)");
		
		return rgbTable;
	}
	
	private static final void plotColorProfiles(ResultsTable table)
	{
		// create x values
		int n = table.getCounter();
		double x[] = new double[n];
		double y[] = new double[n];
		for (int i = 0; i < n; i++) 
		{
			x[i] = i;
			y[i] = 0;
		}
		
		// Compute the max extent of all 3 colors
		double yMax = 0;
		for (int i = 0; i < n; i++) 
		{
			yMax = Math.max(yMax, table.getValueAsDouble(0, i));
			yMax = Math.max(yMax, table.getValueAsDouble(1, i));
			yMax = Math.max(yMax, table.getValueAsDouble(2, i));
		}

		// create plot with default line
		Plot plot = new Plot("Color Profiles", "Region index", "Values", 
				x, y);

		// set up plot
		plot.setLimits(0, n, 0, yMax);

		// Draw each profile
		plot.setColor(Color.RED);
		plot.addPoints(x, table.getColumnAsDoubles(0), Plot.LINE);
		plot.setColor(Color.GREEN);
		plot.addPoints(x, table.getColumnAsDoubles(1), Plot.LINE);
		plot.setColor(Color.BLUE);
		plot.addPoints(x, table.getColumnAsDoubles(2), Plot.LINE);

		// Display in new window
		plot.show();
	}
}
