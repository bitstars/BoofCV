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

package gecv.alg.transform.pyramid;

import gecv.abst.filter.derivative.ImageGradient;
import gecv.core.image.inst.FactoryImageGenerator;
import gecv.struct.image.ImageBase;
import gecv.struct.pyramid.ImagePyramid;


/**
 * Updates two pyramids using the gradient of an input pyramid.
 *
 * @author Peter Abeles
 */
public class GradientPyramid<In extends ImageBase , Out extends ImageBase> {


	protected ImageGradient<In,Out> filter;
	protected Class<Out> derivType;

	public GradientPyramid(ImageGradient<In, Out> filter , Class<Out> derivType ) {
		this.filter = filter;
		this.derivType = derivType;
	}

	public void update( ImagePyramid<In> input ,
						ImagePyramid<Out> derivX,
						ImagePyramid<Out> derivY ) {

		if( !derivX.isLayersDeclared() ) {
			derivX.declareLayers(FactoryImageGenerator.create(derivType),input.bottomWidth,input.bottomHeight );
		}
		if( !derivY.isLayersDeclared() ) {
			derivY.declareLayers(FactoryImageGenerator.create(derivType),input.bottomWidth,input.bottomHeight );
		}

		for( int i = 0; i < input.getNumLayers(); i++ ) {
			In imageIn = input.getLayer(i);
			Out imageOutX = derivX.getLayer(i);
			Out imageOutY = derivY.getLayer(i);

			filter.process(imageIn,imageOutX,imageOutY);
		}
	}

}
