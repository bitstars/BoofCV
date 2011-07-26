/*
 * Copyright 2011 Peter Abeles
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package gecv.alg.detect.corner.impl;

import gecv.struct.image.ImageFloat32;

/**
 * @author Peter Abeles
 */
public class ImplLaplaceInterestPoints {

	public static void determinant( ImageFloat32 featureIntensity , ImageFloat32 hessianXX, ImageFloat32 hessianYY , ImageFloat32 hessianXY ) {
		final int width = hessianXX.width;
		final int height = hessianXX.height;

		if( featureIntensity == null ) {
			featureIntensity = new ImageFloat32(width,height);
		}

		for( int y = 0; y < height; y++ ) {
			int indexXX = hessianXX.startIndex + y*hessianXX.stride;
			int indexYY = hessianYY.startIndex + y*hessianYY.stride;
			int indexXY = hessianXY.startIndex + y*hessianXY.stride;

			int indexInten = featureIntensity.startIndex + y*featureIntensity.stride;

			for( int x = 0; x < width; x++ ) {
				float dxx = hessianXX.data[indexXX++];
				float dyy = hessianYY.data[indexYY++];
				float dxy = hessianXY.data[indexXY++];

				featureIntensity.data[indexInten++] = dxx*dyy - dxy*dxy;
			}
		}
	}

	public static void trace( ImageFloat32 featureIntensity , ImageFloat32 hessianXX, ImageFloat32 hessianYY ) {
		final int width = hessianXX.width;
		final int height = hessianXX.height;

		if( featureIntensity == null ) {
			featureIntensity = new ImageFloat32(width,height);
		}

		for( int y = 0; y < height; y++ ) {
			int indexXX = hessianXX.startIndex + y*hessianXX.stride;
			int indexYY = hessianYY.startIndex + y*hessianYY.stride;

			int indexInten = featureIntensity.startIndex + y*featureIntensity.stride;

			for( int x = 0; x < width; x++ ) {
				float dxx = hessianXX.data[indexXX++];
				float dyy = hessianYY.data[indexYY++];

				featureIntensity.data[indexInten++] = Math.abs(dxx + dyy);
			}
		}
	}

}
