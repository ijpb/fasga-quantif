/**
 * 
 */
package inra.ijpb.appli.fasga;

import java.awt.Rectangle;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/**
 * Comptue the average color in an image, or within the ROI.
 * @author David Legland
 *
 */
public class AverageColorPlugin implements PlugIn {

	/* (non-Javadoc)
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg) {
		// Check an image was open
		ImagePlus image = WindowManager.getCurrentImage();
		if (image == null) {
			IJ.error("No image", "Need at least one image to work");
			return;
		}

		// Execute core of the plugin
		ResultsTable colors = null;
		try {
			colors = exec(image);
		} catch (IllegalArgumentException ex) {
			IJ.error("Image Type Error", ex.getMessage());
			return;
		}
		
		if (colors == null)
			return;

		// display data table
		String name = image.getShortTitle();
		colors.show(name + " Colors");
	}

	public ResultsTable exec(ImagePlus image) {
		ImageProcessor proc = image.getProcessor();
		proc.setRoi(image.getRoi());
		return this.applyTo(proc);
	}

	public ResultsTable applyTo(ImageProcessor image) {
		// get image size
		int width = image.getWidth(); 
		int height = image.getHeight(); 

		// check image type
		if (!(image instanceof ColorProcessor)) {
			throw new IllegalArgumentException("Input image must be color");
		}
		
		// declare variables
		double sumR = 0;
		double sumG = 0;
		double sumB = 0;
		int count = 0;

		
		ImageProcessor mask = image.getMask();
		
		// Get the ROI, that can be null
		int[] rgbArray = new int[3];
		if (mask == null) {
			// iterate over pixels, and update data of RGB counts
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					image.getPixel(x, y, rgbArray);

					sumR += rgbArray[0];
					sumG += rgbArray[1];
					sumB += rgbArray[2];
					count++;
				}
			}
			
		} else {
			// limits of the ROI
			Rectangle rect = image.getRoi();
			int x0 = rect.x;
			int y0 = rect.y;

			// iterate over pixels, and update data of RGB counts
			for (int x = 0; x < rect.width; x++) {
				for (int y = 0; y < rect.height; y++) {

					// Check we are in roi
					if (mask != null) {
						if (mask.get(x, y) == 0)
							continue;
					}

					image.getPixel(x+x0, y+y0, rgbArray);

					sumR += rgbArray[0];
					sumG += rgbArray[1];
					sumB += rgbArray[2];
					count++;
				}
			}
		}
		
		// Initialize a new result table
		ResultsTable result = new ResultsTable();

		// compute mean values
		double value;
		
		// add an entry to the resulting data table
		result.incrementCounter();

		// add mean red
		value = sumR / count;			
		result.addValue("Mean_Red", value);

		// add mean red
		value = sumG / count;			
		result.addValue("Mean_Green", value);

		// add mean red
		value = sumB / count;			
		result.addValue("Mean_Blue", value);


		return result;

	}

	public ResultsTable applyTo(ImageProcessor image, ImageProcessor mask) {
		// get image size
		int width = image.getWidth(); 
		int height = image.getHeight(); 

		// check image type
		if (!(image instanceof ColorProcessor)) {
			throw new IllegalArgumentException("Input image must be color");
		}
		
		// declare variables
		double sumR = 0;
		double sumG = 0;
		double sumB = 0;
		int count = 0;

		
		// Get the ROI, that can be null
		int[] rgbArray = new int[3];

		// iterate over pixels, and update data of RGB counts
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {

				// Check we are in roi
				if (mask.get(x, y) == 0)
					continue;

				image.getPixel(x, y, rgbArray);

				sumR += rgbArray[0];
				sumG += rgbArray[1];
				sumB += rgbArray[2];
				count++;
			}
		}

		// Initialize a new result table
		ResultsTable result = new ResultsTable();

		// compute mean values
		double value;

		// add an entry to the resulting data table
		result.incrementCounter();

		// add mean red
		value = sumR / count;			
		result.addValue("Mean_Red", value);

		// add mean red
		value = sumG / count;			
		result.addValue("Mean_Green", value);

		// add mean red
		value = sumB / count;			
		result.addValue("Mean_Blue", value);


		return result;

	}
}