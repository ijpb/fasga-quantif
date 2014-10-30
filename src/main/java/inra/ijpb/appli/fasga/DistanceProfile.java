package inra.ijpb.appli.fasga;

import ij.IJ;
import ij.measure.ResultsTable;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class DistanceProfile {

	/**
	 * Converts an image of distance map to an image containing index of
	 * distance classes.
	 * 
	 * @param image
	 *            input distance map
	 * @param nClasses
	 *            number of classes
	 * @return a scalar image containing index of classes
	 */
	public static final ImageProcessor distanceMapToClasses(ImageProcessor image, 
			int nClasses) {
		// Compute image size
		int width = image.getWidth();
		int height = image.getHeight();

		// Create new processor for storing result class labels
		ImageProcessor result;
		if (nClasses < 256) {
			result = new ByteProcessor(width, height);
		} else if (nClasses < 256 * 256) {
			result = new ShortProcessor(width, height);
		} else {
			IJ.error("Too many classes");
			return null;
		}
		
		// Compute max value within the image
		double maxDist = 0;
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				maxDist = Math.max(maxDist, image.getf(x, y));
			}
		}
		
		// Compute width of the classes
		double classWidth = maxDist / nClasses;
		
		// compute index of each pixel
		double inputValue;
		int classIndex;
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				inputValue = Math.min(image.getf(x, y), maxDist);
				classIndex = (int) Math.ceil(inputValue / classWidth);
				result.set(x, y, classIndex);
			}
		}
	
		// calibrate min and max values of result image processor
		result.setMinAndMax(0, nClasses);

		// Forces the display to non-inverted LUT
		if (result.isInvertedLut())
			result.invertLut();
		
		return result;
	}
	
	/**
	 * Computes the intensity profile with respect to the regions given by
	 * second argument.
	 * 
	 * @param image
	 *            an image processor containing the values
	 * @param regions
	 *            a gray scale image processor containing region indices
	 * @return a data table with as many rows as the number of regions, and the
	 *         average values of input image by region
	 * @throws IllegalArgumentException
	 *             if image sizes differ
	 */
	public static ResultsTable intensityByRegion(ImageProcessor image, ImageProcessor regions) {
		// get image size
		int width = image.getWidth(); 
		int height = image.getHeight(); 
		
		// check image type
		if (image instanceof ColorProcessor) {
			throw new IllegalArgumentException(
					"Input image must be scalar");
		}

		// check image sizes
		if (regions.getWidth() != width || regions.getHeight() != height) {
			throw new IllegalArgumentException(
					"Input images must have the same size");
		}
		
		// First compute number of regions
		int nRegions = 0;
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				nRegions = Math.max(nRegions, regions.get(x, y));
			}
		}

		// allocate memory
		double[] sums = new double[nRegions+1];
		int[] counts = new int[nRegions+1];

		// iterate over pixels, and update data of corresponding regions
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				int region = regions.get(x, y);
				if (region == 0)
					continue;
				sums[region-1] += image.getf(x, y);
				counts[region-1] ++;
			}
		}
			
		// Initialize a new result table
		ResultsTable result = new ResultsTable();
		for (int i = 0; i < nRegions; i++) {
			double value = sums[i] / counts[i];
			
			// add an entry to the resulting data table
			result.incrementCounter();
			result.addValue("Mean_Gray", value);
		}

		return result;
	}
	
	/**
	 * Computes the profiles of red, green and blue average intensities with
	 * respect to the regions given by second argument.
	 * 
	 * 
	 * @param image
	 *            an image processor containing the values
	 * @param regions
	 *            a gray scale image processor containing region indices
	 * @return a data table with as many rows as the number of regions, and the
	 *         average values of input image by region
	 * @throws IllegalArgumentException
	 *             if image sizes differ
	 */
	public static ResultsTable colorByRegion(ColorProcessor image, ImageProcessor regions) {
		// get image size
		int width = image.getWidth(); 
		int height = image.getHeight(); 
		
		// check image type
		if (!(image instanceof ColorProcessor)) {
			throw new IllegalArgumentException(
					"Input image must be color");
		}

		// check image sizes
		if (regions.getWidth() != width || regions.getHeight() != height) {
			throw new IllegalArgumentException(
					"Input images must have the same size");
		}
		
		// First compute number of regions
		int nRegions = 0;
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				nRegions = Math.max(nRegions, regions.get(x, y));
			}
		}

		// allocate memory
		double[] sumR = new double[nRegions+1];
		double[] sumG = new double[nRegions+1];
		double[] sumB = new double[nRegions+1];
		int[] counts = new int[nRegions+1];

		// iterate over pixels, and update data of corresponding regions
		int[] rgbArray = new int[3];
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				int region = regions.get(x, y);
				if (region == 0)
					continue;
				
				int r = region - 1;
				image.getPixel(x, y, rgbArray);
				sumR[r] += rgbArray[0];
				sumG[r] += rgbArray[1];
				sumB[r] += rgbArray[2];
				counts[r] ++;
			}
		}
			
		// Initialize a new result table
		ResultsTable result = new ResultsTable();
		
		// compute mean values for each region
		double value;
		for (int i = 0; i < nRegions; i++) {
			// add an entry to the resulting data table
			result.incrementCounter();
			
			// add mean red
			value = sumR[i] / counts[i];			
			result.addValue("Mean_Red", value);
			
			// add mean red
			value = sumG[i] / counts[i];			
			result.addValue("Mean_Green", value);
			
			// add mean red
			value = sumB[i] / counts[i];			
			result.addValue("Mean_Blue", value);
		}
		
		return result;
	}
}
