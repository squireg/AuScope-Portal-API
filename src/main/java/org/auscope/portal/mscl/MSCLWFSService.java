package org.auscope.portal.mscl;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethodBase;
import org.auscope.portal.core.server.http.HttpServiceCaller;
import org.auscope.portal.core.services.BaseWFSService;
import org.auscope.portal.core.services.methodmakers.WFSGetFeatureMethodMaker;
import org.auscope.portal.core.services.methodmakers.WFSGetFeatureMethodMaker.ResultType;
import org.auscope.portal.core.services.namespaces.IterableNamespace;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.PropertyIsBetween;
import org.opengis.filter.PropertyIsEqualTo;
import org.opengis.filter.spatial.Intersects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import com.vividsolutions.jts.geom.Envelope;

/**
 * This class is a specialisation of BaseWFSService. Its purpose is to allow access to MSCL's
 * observations WFS. It circumvents an issue with the BaseWFSService's generateWFSRequest(...) 
 * implementation by allowing a null or blank SRS. The other implementation converted such SRSs
 * to a default value which causes a reprojection to occur later on which it would always fail.
 * @author bro879
 */
@Service
public class MSCLWFSService extends BaseWFSService {
	/**
     * Creates a new instance of this class with the specified dependencies
     * @param httpServiceCaller Will be used for making requests
     * @param wfsMethodMaker Will be used for generating WFS methods
     */
	@Autowired
	public MSCLWFSService(
			HttpServiceCaller httpServiceCaller,
			WFSGetFeatureMethodMaker wfsMethodMaker) {
		super(httpServiceCaller, wfsMethodMaker);
	}

	/**
	 * @param serviceUrl
	 * 		The URL of the WFS's endpoint. It should be of the form: http://{domain}:{port}/{path}/wfs
	 * @param featureType
	 * 		The name of the feature type you wish to request (including its prefix if necessary).
	 * @param featureId
	 * 		The ID of the feature you want to return.
	 * @return
	 * 		The response of a WFS query as a string of XML.
	 * @throws ConnectException
	 * @throws ConnectTimeoutException
	 * @throws UnknownHostException
	 * @throws Exception
	 */
	public String getWFSReponse(
			String serviceUrl, 
			String featureType,
			String featureId) throws ConnectException, ConnectTimeoutException, UnknownHostException, Exception {
		
		HttpMethodBase method = this.generateWFSRequest(
				serviceUrl,
				featureType,
				featureId,
				"",
				null,
				null,
				WFSGetFeatureMethodMaker.ResultType.Results,
				"");
		
		return httpServiceCaller.getMethodResponseAsString(method);
	}
	
	public String getObservations(
	        final String serviceUrl,
	        final String boreholeName,
	        final String startDepth,
	        final String endDepth) throws ConnectException, ConnectTimeoutException, UnknownHostException, Exception {
	    // .../wfs?request=GetFeature&typename=mscl:scanned_data&filter=%3CFilter%3E%0D%0A%09%3CPropertyIs%3E%0D%0A%09%09%3CPropertyName%3Emscl%3Aborehole%3C%2FPropertyName%3E%0D%0A%09%09%3CLiteral%3EPRC-5%3C%2FLiteral%3E%0D%0A%09%3C%2FPropertyIs%3E%0D%0A%09%3CPropertyIsBetween%3E%0D%0A%09%09%3CPropertyName%3Emscl%3Adepth%3C%2FPropertyName%3E%0D%0A%09%09%3CLowerBoundary%3E%0D%0A%09%09%09%3CLiteral%3E66.9%3C%2FLiteral%3E%0D%0A%09%09%3C%2FLowerBoundary%3E%0D%0A%09%09%3CUpperBoundary%3E%0D%0A%09%09%09%3CLiteral%3E89%3C%2FLiteral%3E%0D%0A%09%09%3C%2FUpperBoundary%3E%0D%0A%09%3C%2FPropertyIsBetween%3E%0D%0A%3C%2FFilter%3E

        String filterString = String.format( 
            "<Filter>" +
            "   <PropertyIs>" +
            "       <PropertyName>mscl:borehole</PropertyName>" +
            "       <Literal>%s</Literal>" +
            "   </PropertyIs>" +
            "   <PropertyIsBetween>" +
            "       <PropertyName>mscl:depth</PropertyName>" +
            "       <LowerBoundary>" +
            "           <Literal>%s</Literal>" +
            "       </LowerBoundary>" +
            "       <UpperBoundary>" +
            "           <Literal>%s</Literal>" +
            "       </UpperBoundary>" +
            "   </PropertyIsBetween>" +
            "</Filter>", 
            boreholeName,
            startDepth,
            endDepth);

	    HttpMethodBase method = this.generateWFSRequest(
	        serviceUrl,
            "mscl:scanned_data", // TODO: hard-coding feels bad but this is, after all, an MSCL-specific service...
            null,
            filterString,
            null,
            null,
            WFSGetFeatureMethodMaker.ResultType.Results,
            "");
	    
	    return httpServiceCaller.getMethodResponseAsString(method);
	}
	
	/**
	 * Generates a WFS request based on the arguments provided.
	 * @param wfsUrl
	 * 		The URL of the WFS's endpoint. It should be of the form: http://{domain}:{port}/{path}/wfs
	 * @param featureType
	 * 		The name of the feature type you wish to request (including its prefix if necessary).
	 * @param featureId
	 * 		The ID of the feature you want to return.
	 * @param filterString
	 * 		A filter string, if required.
	 * @param maxFeatures
	 * 		The maximum number of features to return.
	 * @param srs
	 * 		The SRS.
	 * @param resultType
	 * 		The desired result type.
	 * @param outputFormat
	 * 		The desired output format.
	 * @return
	 * 		A HttpMethodBase object that encodes the request arguments as an HTTP request.
	 */
	@Override
	protected HttpMethodBase generateWFSRequest(
			String wfsUrl,
			String featureType, 
			String featureId,
			String filterString,
			Integer maxFeatures,
			String srs,
			ResultType resultType,
			String outputFormat) {
		int max = maxFeatures == null ? 0 : maxFeatures.intValue();

        if (featureId == null) {
            return wfsMethodMaker.makePostMethod(wfsUrl, featureType, filterString, max, srs, resultType, outputFormat);
        } else {
            return wfsMethodMaker.makeGetMethod(wfsUrl, featureType, featureId, srs, outputFormat);
        }
    }
}