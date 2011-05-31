/*
 * This file is part of the AuScope Virtual Rock Lab (VRL) project.
 * Copyright (c) 2009 ESSCC, The University of Queensland
 *
 * Licensed under the terms of the GNU Lesser General Public License.
 */
package org.auscope.portal.server.web.controllers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.auscope.portal.server.gridjob.FileInformation;
import org.auscope.portal.server.gridjob.GeodesyJob;
import org.auscope.portal.server.gridjob.GeodesyJobManager;
import org.auscope.portal.server.gridjob.GeodesySeries;
import org.auscope.portal.server.util.PortalPropertyPlaceholderConfigurer;
import org.jets3t.service.S3Service;
import org.jets3t.service.S3ServiceException;
import org.jets3t.service.ServiceException;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.model.S3Bucket;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.security.ProviderCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

/**
 * Controller for the job list view.
 *
 * @author Cihan Altinay
 * @author Abdi Jama
 */
@Controller
public class JobListController {

    /** Logger for this class */
    private final Log logger = LogFactory.getLog(getClass());

    @Autowired
    private GeodesyJobManager jobManager;
    @Autowired
    @Qualifier(value = "propertyConfigurer")
    private PortalPropertyPlaceholderConfigurer hostConfigurer;
    
    private S3Service s3Service;
    private AmazonEC2 ec2;

    /**
     * Delete the job given by its reference.
     *
     * @param request The servlet request including a jobId parameter
     * @param response The servlet response
     *
     * @return A JSON object with a success attribute and an error attribute
     *         in case the job was not found or can not be deleted.
     */
    @RequestMapping("/deleteJob.do")
    public ModelAndView deleteJob(HttpServletRequest request,
                                HttpServletResponse response) {
    	String userEmail = (String)request.getSession().getAttribute("openID-Email");
        String jobIdStr = request.getParameter("jobId");
        GeodesyJob job = null;
        ModelAndView mav = new ModelAndView("jsonView");
        boolean success = false;

        if (jobIdStr != null) {
            try {
                int jobId = Integer.parseInt(jobIdStr);
                job = jobManager.getJobById(jobId);
            } catch (NumberFormatException e) {
                logger.error("Error parsing job ID!");
            }
        } else {
            logger.warn("No job ID specified!");
        }

        if (job == null) {
            final String errorString = "The requested job was not found.";
            logger.error(errorString);
            mav.addObject("error", errorString);

        } else {
            // check if current user is the owner of the job
            GeodesySeries s = jobManager.getSeriesById(job.getSeriesId());
            if (userEmail.equals(s.getUser())) {
                logger.info("Deleting job with ID "+jobIdStr);
                jobManager.deleteJob(job);
                success = true;
            } else {
                logger.warn(userEmail+"'s attempt to kill "+
                        s.getUser()+"'s job denied!");
                mav.addObject("error", "You are not authorised to delete this job.");
            }
        }
        mav.addObject("success", success);

        return mav;
    }
    /**
     * delete all jobs of given series.
     *
     * @param request The servlet request including a seriesId parameter
     * @param response The servlet response
     *
     * @return A JSON object with a success attribute and an error attribute
     *         in case the series was not found in the job manager.
     */
    @RequestMapping("/deleteSeriesJobs.do")
    public ModelAndView deleteSeriesJobs(HttpServletRequest request,
                                       HttpServletResponse response) {
    	
    	String userEmail = (String)request.getSession().getAttribute("openID-Email");
        String seriesIdStr = request.getParameter("seriesId");
        List<GeodesyJob> jobs = null;
        ModelAndView mav = new ModelAndView("jsonView");
        boolean success = false;
        int seriesId = -1;

        if (seriesIdStr != null) {
            try {
                seriesId = Integer.parseInt(seriesIdStr);
                jobs = jobManager.getSeriesJobs(seriesId);
            } catch (NumberFormatException e) {
                logger.error("Error parsing series ID!");
            }
        } else {
            logger.warn("No series ID specified!");
        }

        if (jobs == null) {
            final String errorString = "The requested series was not found.";
            logger.error(errorString);
            mav.addObject("error", errorString);
            mav.addObject("success", false);

        } else {
            // check if current user is the owner of the series
            GeodesySeries s = jobManager.getSeriesById(seriesId);
            if (userEmail.equals(s.getUser())) {
                logger.info("Deleting jobs of series "+seriesIdStr);
                boolean jobsDeleted = true;
                for (GeodesyJob job : jobs) {
                    String oldStatus = job.getStatus();
                    if (oldStatus.equals("Failed") || oldStatus.equals("Done") ||
                            oldStatus.equals("Cancelled")) {
                        jobManager.deleteJob(job);
                        
                    }else{
                    	logger.debug("Skipping running job "+job.getId());
                    	if(jobsDeleted){
                    		jobsDeleted = false;
                    		mav.addObject("error", "Can not delete series, there are running jobs.");
                    	}        	
                    	continue;                  	
                    }
                }
                if(jobsDeleted){
                	logger.info("Deleting series "+seriesIdStr);
                	jobManager.deleteSeries(s);
                	logger.info("Deleted series "+seriesIdStr);
                	success = true;
                }else{
                	success = false;
                }
            } else {
                logger.warn(userEmail+"'s attempt to delete "+
                        s.getUser()+"'s jobs denied!");
                mav.addObject("error", "You are not authorised to delete the jobs of this series.");
            }
        }

        mav.addObject("success", success);
        return mav;
    }
    
    /**
     * Kills the job given by its reference.
     *
     * @param request The servlet request including a jobId parameter
     * @param response The servlet response
     *
     * @return A JSON object with a success attribute and an error attribute
     *         in case the job was not found in the job manager.
     */
    @RequestMapping("/killJob.do")
    public ModelAndView killJob(HttpServletRequest request,
                                HttpServletResponse response) {

    	String userEmail = (String)request.getSession().getAttribute("openID-Email");
        String jobIdStr = request.getParameter("jobId");
        GeodesyJob job = null;
        ModelAndView mav = new ModelAndView("jsonView");
        boolean success = false;

        if (jobIdStr != null) {
            try {
                int jobId = Integer.parseInt(jobIdStr);
                job = jobManager.getJobById(jobId);
            } catch (NumberFormatException e) {
                logger.error("Error parsing job ID!");
            }
        } else {
            logger.warn("No job ID specified!");
        }

        if (job == null) {
            final String errorString = "The requested job was not found.";
            logger.error(errorString);
            mav.addObject("error", errorString);

        } else {
            // check if current user is the owner of the job
            GeodesySeries s = jobManager.getSeriesById(job.getSeriesId());
            if (userEmail.equals(s.getUser())) {
                logger.info("Cancelling job with ID "+jobIdStr);
                
                // terminate the EMI instance
                try {
					terminateInstance(request, job);
	                success = true;
	                
				} catch (AmazonServiceException e) {
					final String errorString = "Failed to terminate instance with id: " + job.getReference();
					logger.error(errorString);
					mav.addObject(errorString);
				} 
            } else {
                logger.warn(userEmail+"'s attempt to kill "+
                        s.getUser()+"'s job denied!");
                mav.addObject("error", "You are not authorised to cancel this job.");
            }
        }
        mav.addObject("success", success);

        return mav;
    }
    
    
	/**
	 * Terminates the instance of an EMI that was launched by a job.
	 * 
	 * @param request The HttpServletRequest
	 * @param job The job linked the to instance that is to be terminated
	 */
	private void terminateInstance(HttpServletRequest request, GeodesyJob job) {
		
		if (ec2 == null) {
			AWSCredentials credentials = (AWSCredentials)request.getSession().getAttribute("AWSCred");
			ec2 = new AmazonEC2Client(credentials);
			ec2.setEndpoint(hostConfigurer.resolvePlaceholder("ec2.endpoint"));
		}
		
		TerminateInstancesRequest termReq = new TerminateInstancesRequest();
		ArrayList<String> instanceIdList = new ArrayList<String>();
		instanceIdList.add(job.getReference());
		termReq.setInstanceIds(instanceIdList);
		ec2.terminateInstances(termReq);
							
		job.setStatus("Cancelled");
		jobManager.saveJob(job);
	}

    /**
     * Kills all jobs of given series.
     *
     * @param request The servlet request including a seriesId parameter
     * @param response The servlet response
     *
     * @return A JSON object with a success attribute and an error attribute
     *         in case the series was not found in the job manager.
     */
    @RequestMapping("/killSeriesJobs.do")
    public ModelAndView killSeriesJobs(HttpServletRequest request,
                                       HttpServletResponse response) {

    	String userEmail = (String)request.getSession().getAttribute("openID-Email");
        String seriesIdStr = request.getParameter("seriesId");
        List<GeodesyJob> jobs = null;
        ModelAndView mav = new ModelAndView("jsonView");
        boolean success = false;
        int seriesId = -1;
        
        if (seriesIdStr != null) {
            try {
                seriesId = Integer.parseInt(seriesIdStr);
                jobs = jobManager.getSeriesJobs(seriesId);
            } catch (NumberFormatException e) {
                logger.error("Error parsing series ID!");
            }
        } else {
            logger.warn("No series ID specified!");
        }

        if (jobs == null) {
            final String errorString = "The requested series was not found.";
            logger.error(errorString);
            mav.addObject("error", errorString);
            mav.addObject("success", false);

        } else {
            // check if current user is the owner of the series
            GeodesySeries s = jobManager.getSeriesById(seriesId);
            if (userEmail.equals(s.getUser())) {
                logger.info("Cancelling jobs of series "+seriesIdStr);
                for (GeodesyJob job : jobs) {
                    String oldStatus = job.getStatus();
                    if (oldStatus.equals("Failed") || oldStatus.equals("Done") ||
                            oldStatus.equals("Cancelled")) {
                        logger.debug("Skipping finished job "+job.getId());
                        continue;
                    }
                    logger.info("Cancelling job with ID "+job.getId());
                    
                    // terminate the EMI instance
                    try {
    					terminateInstance(request, job);
    	                success = true;
    	                
    				} catch (AmazonServiceException e) {
    					final String errorString = "Failed to terminate instance with id: " + job.getReference();
    					logger.error(errorString);
    					mav.addObject(errorString);
    				} 
                }
                success = true;
            } else {
                logger.warn(userEmail+"'s attempt to kill "+
                        s.getUser()+"'s jobs denied!");
                mav.addObject("error", "You are not authorised to cancel the jobs of this series.");
            }
        }

        mav.addObject("success", success);
        return mav;
    }

    /**
     * Returns a JSON object containing an array of files belonging to a
     * given job.
     *
     * @param request The servlet request including a jobId parameter
     * @param response The servlet response
     *
     * @return A JSON object with a files attribute which is an array of
     *         FileInformation objects. If the job was not found in the job
     *         manager the JSON object will contain an error attribute
     *         indicating the error.
     */
    @RequestMapping("/jobFiles.do")
    public ModelAndView jobFiles(HttpServletRequest request,
                                 HttpServletResponse response) {

    	GeodesyJob job = null;
        ModelAndView mav = new ModelAndView("jsonView");
        String jobIdStr = request.getParameter("jobId");
        logger.debug("jobIdStr: " + jobIdStr);
        int  totalItems = 0;
        
        if (jobIdStr != null) {
            try {
                int jobId = Integer.parseInt(jobIdStr);
                job = jobManager.getJobById(jobId);
            } catch (NumberFormatException e) {
                logger.error("Error parsing job ID!");
            }
        } else {
            logger.warn("No job ID specified!");
        }
        
        if (job == null) {
            final String errorString = "The requested job was not found.";
            logger.error("The requested job was not found.");
            mav.addObject("error", errorString);
        } else if (job.getOutputDir() != null) {
        	// get results file information to display in the Files tab
        	S3Object[] results = getOutputFileDetails(request, job);
        	FileInformation[] fileDetails = new FileInformation[results.length];
        	
        	int i = 0;
        	// get file information from s3 objects
        	for (S3Object object : results) {
        		fileDetails[i++] = new FileInformation(object.getKey(), object.getContentLength());
        		totalItems++;
        	}
        	
        	logger.info(totalItems + " job files located");
        	mav.addObject("files", fileDetails);
        }
        
    	return mav;
    }
    

    /**
     * Sends the contents of a job file to the client.
     *
     * @param request The servlet request including a jobId parameter and a
     *                filename parameter
     * @param response The servlet response receiving the data
     *
     * @return null on success or the joblist view with an error parameter on
     *         failure.
     */
    @RequestMapping("/downloadFile.do")
    public ModelAndView downloadFile(HttpServletRequest request,
                                     HttpServletResponse response) {

        String jobIdStr = request.getParameter("jobId");
        String fileName = request.getParameter("filename");
        String key = request.getParameter("key");
        GeodesyJob job = null;
        String errorString = null;

        if (jobIdStr != null) {
            try {
                int jobId = Integer.parseInt(jobIdStr);
                job = jobManager.getJobById(jobId);
            } catch (NumberFormatException e) {
                logger.error("Error parsing job ID!");
            }
        }

        if (job != null && fileName != null) {
        	
        	logger.debug("Download " + key);
        	try {
				S3Service s3Service = getS3Service(request);
				S3Object s3obj = s3Service.getObject(GridSubmitController.S3_BUCKET_NAME, key);
				InputStream is = s3obj.getDataInputStream();
					
				if (is != null) {
					response.setContentType("application/octet-stream");
				    response.setHeader("Content-Disposition",
				            "attachment; filename=\""+fileName+"\"");
				    
				    try {
				    	OutputStream out = response.getOutputStream();
				        int n;
				        byte[] buffer = new byte[1024];
				     
				        while ((n = is.read(buffer)) != -1) {
				        	out.write(buffer, 0, n);
				        }
				        
				        out.flush();
				        return null;
				        
				    } catch(IOException e) {
				    	errorString = new String("Could not send file: " +
				                e.getMessage());
				        logger.error(errorString);
				    } finally {
				    	IOUtils.closeQuietly(is);
				    }
				}
				else{
					logger.error("inputstream is null");
				}
			} catch (S3ServiceException e) {
				errorString = new String("Error creating S3Service: " +
		                e.getMessage());
		        logger.error(errorString);
			} catch (ServiceException e) {
				errorString = new String("Error getting S3Object data: " +
		                e.getMessage());
		        logger.error(errorString);
			}
        }

        // We only end up here in case of an error so return a suitable message
        if (errorString == null) {
            if (job == null) {
                errorString = new String("Invalid job specified!");
                logger.error(errorString);
            } else if (fileName == null) {
                errorString = new String("No filename provided!");
                logger.error(errorString);
            } else {
                // should never get here
                errorString = new String("Something went wrong.");
                logger.error(errorString);
            }
        }
        return new ModelAndView("joblist", "error", errorString);
    }

    /**
     * Sends the contents of one or more job files as a ZIP archive to the
     * client.
     *
     * @param request The servlet request including a jobId parameter and a
     *                files parameter with the filenames separated by comma
     * @param response The servlet response receiving the data
     *
     * @return null on success or the joblist view with an error parameter on
     *         failure.
     */
    @RequestMapping("/downloadAsZip.do")
    public ModelAndView downloadAsZip(HttpServletRequest request,
                                      HttpServletResponse response) {

        String jobIdStr = request.getParameter("jobId");
        String filesParam = request.getParameter("files");
        logger.debug("filesParam: " + filesParam);
        GeodesyJob job = null;
        String errorString = null;

        if (jobIdStr != null) {
            try {
                int jobId = Integer.parseInt(jobIdStr);
                job = jobManager.getJobById(jobId);
            } catch (NumberFormatException e) {
                logger.error("Error parsing job ID!");
            }
        }

        if (job != null && filesParam != null) {
            String[] fileKeys = filesParam.split(",");
            logger.debug("Archiving " + fileKeys.length + " file(s) of job " +
                    jobIdStr);

            response.setContentType("application/zip");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"jobfiles.zip\"");
            
            try {

            	S3Service s3Service = getS3Service(request);
                boolean readOneOrMoreFiles = false;
                ZipOutputStream zout = new ZipOutputStream(
                        response.getOutputStream());
                for (String fileKey : fileKeys) {
                	
                	S3Object s3obj = s3Service.getObject(GridSubmitController.S3_BUCKET_NAME, fileKey);
               		InputStream is = s3obj.getDataInputStream();
                    
                    byte[] buffer = new byte[16384];
                    int count = 0;
                    zout.putNextEntry(new ZipEntry(fileKey));
                    while ((count = is.read(buffer)) != -1) {
                        zout.write(buffer, 0, count);
                    }
                    zout.closeEntry();
                    readOneOrMoreFiles = true;
                }
                if (readOneOrMoreFiles) {
                    zout.finish();
                    zout.flush();
                    zout.close();
                    return null;

                } else {
                    zout.close();
                    errorString = new String("Could not access the files!");
                    logger.error(errorString);
                }

            } catch (IOException e) {
                errorString = new String("Could not create ZIP file: " +
                        e.getMessage());
                logger.error(errorString);
            } catch (ServiceException e) {
            	errorString = new String("Error getting S3Object data: " +
		                e.getMessage());
		        logger.error(errorString);
			}
        }

        // We only end up here in case of an error so return a suitable message
        if (errorString == null) {
            if (job == null) {
                errorString = new String("Invalid job specified!");
                logger.error(errorString);
            } else if (filesParam == null) {
                errorString = new String("No filename(s) provided!");
                logger.error(errorString);
            } else {
                // should never get here
                errorString = new String("Something went wrong.");
                logger.error(errorString);
            }
        }
        return new ModelAndView("joblist", "error", errorString);
    }

    /**
     * Returns a JSON object containing an array of series that match the query
     * parameters.
     *
     * @param request The servlet request with query parameters
     * @param response The servlet response
     *
     * @return A JSON object with a series attribute which is an array of
     *         GeodesySeries objects matching the criteria.
     */
    @RequestMapping("/querySeries.do")
    public ModelAndView querySeries(HttpServletRequest request,
                                    HttpServletResponse response) {

        String qUser = request.getParameter("qUser");
        String qName = request.getParameter("qSeriesName");
        String qDesc = request.getParameter("qSeriesDesc");

        if (qUser == null && qName == null && qDesc == null) {
            qUser = (String)request.getSession().getAttribute("openID-Email");//request.getRemoteUser();
            logger.debug("No query parameters provided. Will return "+qUser+"'s series.");
        }

        logger.debug("qUser="+qUser+", qName="+qName+", qDesc="+qDesc);
        List<GeodesySeries> series = jobManager.querySeries(qUser, qName, qDesc);

        logger.debug("Returning list of "+series.size()+" series.");
        return new ModelAndView("jsonView", "series", series);
    }

    /**
     * Returns a JSON object containing an array of jobs for the given series.
     *
     * @param request The servlet request including a seriesId parameter
     * @param response The servlet response
     *
     * @return A JSON object with a jobs attribute which is an array of
     *         <code>GeodesyJob</code> objects.
     */
    @RequestMapping("/listJobs.do")
    public ModelAndView listJobs(HttpServletRequest request,
                                 HttpServletResponse response) {

        String seriesIdStr = request.getParameter("seriesId");
        List<GeodesyJob> seriesJobs = null;
        ModelAndView mav = new ModelAndView("jsonView");
        int seriesId = -1;

        if (seriesIdStr != null) {
            try {
                seriesId = Integer.parseInt(seriesIdStr);
                seriesJobs = jobManager.getSeriesJobs(seriesId);
            } catch (NumberFormatException e) {
                logger.error("Error parsing series ID '"+seriesIdStr+"'");
            }
        } else {
            logger.warn("No series ID specified!");
        }
        
        // check to see if any active jobs have completed or failed so the status can be updated
        if (seriesJobs != null) {
        	
        	for (GeodesyJob job : seriesJobs) {
        		S3Object[] results = getOutputFileDetails(request, job);
        		
        		if (job.getStatus().equals("Active") && results.length > 0) {
        			// update status to done
        			job.setStatus("Done");
        			 
        			// check if an error log exists. If so the job has failed.
        			for (S3Object object : results) {
        				if (object.getName().endsWith("error.log")) {
        					job.setStatus("Failed");
        					break;
        				}
        			}
        			
        			jobManager.saveJob(job);
        		}
        	}
        	
        	mav.addObject("jobs", seriesJobs);
        }

        logger.debug("Returning series job list");
        return mav;
    }
    
    /**
     * Gets the details of any files in the jobs output directory.
     * 
     * @param request The HttpServletRequest
     * @param job The job that that we want results for
     * @return Array of S3Objects, or null if no results available.
     */
    private S3Object[] getOutputFileDetails(HttpServletRequest request, GeodesyJob job) {
        
        try {
			S3Service s3Service = getS3Service(request);
			S3Bucket[] buckets = s3Service.listAllBuckets();
			
			logger.info("job output dir: '" + job.getOutputDir() + "'");
			
			for (S3Bucket bucket : buckets) {
	        	if (bucket.getName().equals(GridSubmitController.S3_BUCKET_NAME)) {
	        		S3Object[] objects = s3Service.listObjects(bucket.getName(), job.getOutputDir(),null);
		        	return objects;
	        	}
	        }
		} catch (S3ServiceException e) {
			logger.error("Error obtaining S3Service.");
			e.printStackTrace();
		}
		
		return null;
    }
    
    /**
     * Get or create a new S3Service for accessing job files in S3.
     *  
     * @param request The HttpServletRequest
     * @return an S3Service object
     * @throws S3ServiceException
     */
    private S3Service getS3Service(HttpServletRequest request) throws S3ServiceException {
    	
    	// create a new service object if we don't have one
    	if (s3Service == null) {
    		AWSCredentials credentials = (AWSCredentials)request.getSession().getAttribute("AWSCred");
            ProviderCredentials provCreds = new org.jets3t.service.security.AWSCredentials(credentials.getAWSAccessKeyId(), credentials.getAWSSecretKey());
            
            s3Service = new RestS3Service(provCreds);
    	}
    	
        return s3Service;
    }
}
