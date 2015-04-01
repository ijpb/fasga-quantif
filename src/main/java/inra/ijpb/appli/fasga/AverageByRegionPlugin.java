package inra.ijpb.appli.fasga;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.awt.Color;


/**
 * Plugin that opens a dialog to choose two images, the first one for input
 * data, the second one for regions, and that computes average intensity or
 * color components in each region.
 * 
 * @see DistanceProfile
 * 
 * @author David Legland
 */
public class AverageByRegionPlugin implements PlugIn
{
	/* (non-Javadoc)
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg) 
	{
		int[] indices = WindowManager.getIDList();
		if (indices==null) 
		{
			IJ.error("No image", "Need at least one image to work");
			return;
		}
		
		// create the list of image names
		String[] imageNames = new String[indices.length];
		for (int i=0; i < indices.length; i++) 
		{
			imageNames[i] = WindowManager.getImage(indices[i]).getTitle();
		}
		
		// create the dialog
		GenericDialog gd = new GenericDialog("Average Color By Region");
		
		gd.addChoice("Input Image", imageNames, IJ.getImage().getTitle());
		gd.addChoice("Regions Image", imageNames, IJ.getImage().getTitle());
		// Could also add an option for the type of operation
		gd.showDialog();
		
		if (gd.wasCanceled())
			return;
		
		// set up current parameters
		int inputImageIndex = gd.getNextChoiceIndex();
		ImagePlus inputImage = WindowManager.getImage(inputImageIndex + 1);
		int regionImageIndex = gd.getNextChoiceIndex();
		ImagePlus regionImage = WindowManager.getImage(regionImageIndex + 1);
		
		if (regionImage.getType() != ImagePlus.GRAY8 
				&& regionImage.getType() != ImagePlus.GRAY16) 
		{
			IJ.showMessage("Region image should be label");
			return;
		}
		
		// Execute core of the plugin
		Object[] res = exec(inputImage, regionImage);

		if (res == null)
			return;

		// show result table if needed
		ResultsTable table = (ResultsTable) res[1];
		table.show((String) res[0]);

		// create x values
		int n = table.getCounter();
		double x[] = new double[n];
		double y[] = new double[n];
		for (int i = 0; i < n; i++) 
		{
			x[i] = i;
			y[i] = 0;
		}
		
		// also draw line graph
		if (inputImage.getType() == ImagePlus.COLOR_RGB) 
		{
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
		else 
		{
			// Compute the max intensity of the profile
			double yMax = 0;
			for (int i = 0; i < n; i++) 
			{
				yMax = Math.max(yMax, table.getValueAsDouble(0, i));
			}
				
			// create plot with default line
			Plot plot = new Plot("Intensity Profiles", "Region index", "Intensity", 
					x, table.getColumnAsDoubles(0));
			
			// set up plot
			plot.setLimits(0, n, 0, yMax);
			
			// Display in new window
			plot.show();			
		}
	}


	/**
	 * Computes the average of input image for each region defined by second
	 * image.
	 * 
	 * @param image
	 *            the image used for computing intensities or colors
	 * @param regions
	 *            the image containing region indices
	 * @return an object array with name computed from image, and a data table
	 *         containing the average intensity or color components for each
	 *         region
	 */
	public Object[] exec(ImagePlus image, ImagePlus regions) 
	{
		// Check validity of parameters
		if (image == null)
			return null;
		if (regions == null)
			return null;
		
		// extract the different processors
		ImageProcessor inputProcessor = image.getProcessor();
		ImageProcessor regionsProcessor = regions.getProcessor();
		
		// Process the plugIn on the ImageProcessors
		ResultsTable table = null;
		if (inputProcessor instanceof ColorProcessor) 
		{
			table = DistanceProfile.colorByRegion(
					(ColorProcessor) inputProcessor, regionsProcessor);
		} 
		else 
		{
			table = DistanceProfile.intensityByRegion(
					inputProcessor, regionsProcessor);
		}
		
		// create string for indexing results
		String tableName = image.getShortTitle() + "-by-region"; 
		
		// return the created array
		return new Object[]{tableName, table};
	}

}
