/**
 * 
 */
package inra.ijpb.appli.fasga;

/**
 * Interface for specification of a color space converter.
 * @author David Legland
 *
 */
public interface ColorSpace
{
	/**
	 * http://www.easyrgb.com/math.html
	 */
	public final static class HSL implements ColorSpace 
	{

		@Override
		public String[] getChannelNames()
		{
			return new String[]{"Hue", "Saturation", "Luminance"};
		}

		@Override
		public int getChannelNumber()
		{
			return 3;
		}

		@Override
		public float[] convertSRGB(float[] sRGB, float[] newValues)
		{
			// Extract input channel values
			float r = sRGB[0];
			float g = sRGB[1];
			float b = sRGB[2];
			
            // Min and Max values of RGB triplet
            float mini = Math.min(Math.min(r, g), b);
            float maxi = Math.max(Math.max(r, g), b);
            
            // Delta RGB value
            float delta = maxi - mini;      

			float H = 0, S = 0, L = 0;

            L =  (maxi + mini) / 2;         

			if (delta == 0)
			{
				// In case of gray value, there is no chroma result
				H = 0f;
				S = 0f;
			} else
			{ 	// Process Chromatic data...
				if (L < 0.5f)
					S = delta / (maxi + mini);
				else
					S = delta / (2f - maxi - mini);

				float del_R = (((maxi - r) / 6f) + (delta / 2f))
						/ delta;
				float del_G = (((maxi - g) / 6f) + (delta / 2f))
						/ delta;
				float del_B = (((maxi - b) / 6f) + (delta / 2f))
						/ delta;

				if (r == maxi)
					H = del_B - del_G;
				else if (g == maxi)
					H = (1f / 3f) + del_R - del_B;
				else if (b == maxi)
					H = (2f / 3f) + del_G - del_R;

				if (H < 0f)
					H += 1f;
				if (H > 1f)
					H -= 1f;
			}
			
			newValues[0] = H;
			newValues[1] = S;
			newValues[2] = L;
			return newValues;
		}

		@Override
		public float[] convertSRGB(float[] sRGB)
		{
			return convertSRGB(sRGB, new float[3]);
		}
		
	}
	
	/**
	 * http://www.easyrgb.com/math.html
	 */
	public final static class HSV implements ColorSpace 
	{

		@Override
		public String[] getChannelNames()
		{
			return new String[]{"Hue", "Saturation", "Value"};
		}

		@Override
		public int getChannelNumber()
		{
			return 3;
		}

		@Override
		public float[] convertSRGB(float[] sRGB, float[] newValues)
		{
			// Extract input channel values
			float r = sRGB[0];
			float g = sRGB[1];
			float b = sRGB[2];
			
			// Min and Max values of RGB triplet
            float mini = Math.min(Math.min(r, g), b);
            float maxi = Math.max(Math.max(r, g), b);
            
            //Delta RGB value
            float delta = maxi - mini;      

			float H = 0, S = 0, V = 0;

            V = maxi * 1f;
			if (delta == 0)
			{
				// In case of gray value, there is no chroma result
				H = 0f;
				S = 0f;
			} else
			{ 	// Process Chromatic data...
				S = delta / maxi;
				float del_R = (((maxi - r) / 6f) + (delta / 2f)) / delta;
				float del_G = (((maxi - g) / 6f) + (delta / 2f)) / delta;
				float del_B = (((maxi - b) / 6f) + (delta / 2f)) / delta;

				if (r == maxi)
					H = del_B - del_G;
				else if (g == maxi)
					H = (1f / 3f) + del_R - del_B;
				else if (b == maxi)
					H = (2f / 3f) + del_G - del_R;

				if (H < 0)
					H += 1;
				if (H > 1)
					H -= 1;
			}

			newValues[0] = H;
			newValues[1] = S;
			newValues[2] = V;
			return newValues;
		}

		@Override
		public float[] convertSRGB(float[] sRGB)
		{
			return convertSRGB(sRGB, new float[3]);
		}
		
	}
	
	/** Return the names of the new channels */ 
	public String[] getChannelNames();

	/** Returns the number of new channels, usually 3 or 4.*/
	public int getChannelNumber();
	
	/** Converts a scaled RGB triplet to a vector in new color space*/
	public float[] convertSRGB(float[] sRGB);
	
	/** Converts a scaled RGB triplet to a vector in new color space*/
	public float[] convertSRGB(float[] sRGB, float[] newValues);
	
}
