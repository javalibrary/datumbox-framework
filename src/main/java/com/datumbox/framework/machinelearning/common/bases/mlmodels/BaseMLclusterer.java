/**
 * Copyright (C) 2013-2015 Vasilis Vryniotis <bbriniotis@datumbox.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datumbox.framework.machinelearning.common.bases.mlmodels;

import com.datumbox.common.dataobjects.Dataset;
import com.datumbox.common.dataobjects.Record;
import com.datumbox.common.objecttypes.Learnable;
import com.datumbox.common.persistentstorage.interfaces.DatabaseConnector;
import com.datumbox.common.persistentstorage.interfaces.DatabaseConfiguration;
import com.datumbox.framework.machinelearning.common.validation.ClustererValidation;
import com.datumbox.framework.machinelearning.common.bases.validation.ModelValidation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 *
 * @author Vasilis Vryniotis <bbriniotis@datumbox.com>
 * @param <CL>
 * @param <MP>
 * @param <TP>
 * @param <VM>
 */
public abstract class BaseMLclusterer<CL extends BaseMLclusterer.Cluster, MP extends BaseMLclusterer.ModelParameters, TP extends BaseMLclusterer.TrainingParameters, VM extends BaseMLclusterer.ValidationMetrics> extends BaseMLmodel<MP, TP, VM> {
    
    public static abstract class Cluster implements Learnable, Iterable<Integer> {
        //variables
        protected Integer clusterId;
        
        
        protected Set<Integer> recordIdSet; 
        
        protected Object labelY = null;
        
        protected Cluster(Integer clusterId) {
            this.clusterId = clusterId;
            recordIdSet = new HashSet<>(); //This is large but it should store only references of the Records not the actualy objects
        }

        
        //Getters
        
        public Integer getClusterId() {
            return clusterId;
        }

        public Set<Integer> getRecordIdSet() {
            return recordIdSet;
        }
        
        public Object getLabelY() {
            return labelY;
        }
        
        //Wrapper methods for the recordIdSet
        
        public int size() {
            if(recordIdSet == null) {
                return 0;
            }
            return recordIdSet.size();
        }
        
        /**
         * Implementing a read-only iterator on recordIdSet to use it in loops.
         * 
         * @return 
         */
        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<Integer>() {
                private final Iterator<Integer> it = recordIdSet.iterator();

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public Integer next() {
                    return it.next();
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
        
        //internal methods
        
        protected void clear() {
            if(recordIdSet!=null) {
                recordIdSet.clear();
            }
        }
        
        protected void setLabelY(Object labelY) {
            this.labelY = labelY;
        }
        
        @Override
        public int hashCode() {
            int hash = 7;
            hash = 89 * hash + this.clusterId;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Cluster other = (Cluster) obj;
            if (!Objects.equals(this.clusterId, other.clusterId)) {
                return false;
            }
            return true;
        }
        
        //abstract methods that modify the cluster set and should update its statistics in each implementation
        
        protected abstract boolean add(Integer rId, Record r);
        
        protected abstract boolean remove(Integer rId, Record r);

    }
    
    public static abstract class ModelParameters<CL extends BaseMLclusterer.Cluster> extends BaseMLmodel.ModelParameters {
        
        //number of classes if the dataset is annotated. Use Linked Hash Set to ensure that the order of classes will be maintained. 
        private Set<Object> goldStandardClasses = new LinkedHashSet<>(); //this is small. Size equal to class numbers;
        
        
        
        
        private Map<Integer, CL> clusterList = new HashMap<>(); //the cluster objects of the model

        protected ModelParameters(DatabaseConnector dbc) {
            super(dbc);
        }
        
        
        //Getters / Setters
        
        public Integer getC() {
            return clusterList.size();
        }

        public Set<Object> getGoldStandardClasses() {
            return goldStandardClasses;
        }

        public void setGoldStandardClasses(Set<Object> goldStandardClasses) {
            this.goldStandardClasses = goldStandardClasses;
        }

        public Map<Integer, CL> getClusterList() {
            return clusterList;
        }

        public void setClusterList(Map<Integer, CL> clusterList) {
            this.clusterList = clusterList;
        }
        
        
    } 
    
    
    public static abstract class TrainingParameters extends BaseMLmodel.TrainingParameters {    

    } 

    public static abstract class ValidationMetrics extends BaseMLmodel.ValidationMetrics {
        //References: http://nlp.stanford.edu/IR-book/html/htmledition/evaluation-of-clustering-1.html
        //http://thesis.neminis.org/wp-content/plugins/downloads-manager/upload/masterThesis-VR.pdf
        private Double purity = null;
        private Double NMI = null; //Normalized Mutual Information: I(Omega,Gama) calculation
        
        public Double getPurity() {
            return purity;
        }

        public void setPurity(Double purity) {
            this.purity = purity;
        }

        public Double getNMI() {
            return NMI;
        }

        public void setNMI(Double NMI) {
            this.NMI = NMI;
        }
        
    }
    
    protected BaseMLclusterer(String dbName, DatabaseConfiguration dbConf, Class<MP> mpClass, Class<TP> tpClass, Class<VM> vmClass) {
        super(dbName, dbConf, mpClass, tpClass, vmClass, new ClustererValidation<>());
    } 
    
    protected BaseMLclusterer(String dbName, DatabaseConfiguration dbConf, Class<MP> mpClass, Class<TP> tpClass, Class<VM> vmClass, ModelValidation<MP, TP, VM> modelValidator) {
        super(dbName, dbConf, mpClass, tpClass, vmClass, modelValidator);
    } 
    
    
    @Override
    protected VM validateModel(Dataset validationData) {
        predictDataset(validationData);
        
        int n = validationData.getRecordNumber();
        
        MP modelParameters = knowledgeBase.getModelParameters();
        Map<Integer, CL> clusterList = modelParameters.getClusterList();
        Set<Object> goldStandardClassesSet = modelParameters.getGoldStandardClasses();
        
        //create new validation metrics object
        VM validationMetrics = knowledgeBase.getEmptyValidationMetricsObject();
        
        if(goldStandardClassesSet.isEmpty()) {
            return validationMetrics;
        }
        
        //We don't store the Contingency Table because we can't average it with
        //k-cross fold validation. Each clustering produces a different number
        //of clusters and thus different enumeration. Thus averaging the results
        //is impossible and that is why we don't store it in the validation object.
        
        //List<Object> = [Clusterid,GoldStandardClass]
        Map<List<Object>, Double> ctMap = new HashMap<>();
        
        //frequency tables
        Map<Integer, Double> countOfW = new HashMap<>(); //this is small equal to number of clusters
        Map<Object, Double> countOfC = new HashMap<>(); //this is small equal to number of classes
        
        //initialize the tables with zeros
        for(CL cluster : clusterList.values()) {
            Integer clusterId = cluster.getClusterId();
            countOfW.put(clusterId, 0.0);
            for(Object theClass : modelParameters.getGoldStandardClasses()) {
                ctMap.put(Arrays.<Object>asList(clusterId, theClass), 0.0);
                
                countOfC.put(theClass, 0.0);
            }
        }
        
        
        //count the co-occurrences of ClusterId-GoldStanardClass
        for(Integer rId : validationData) {
            Record r = validationData.get(rId);
            
            Integer clusterId = (Integer) r.getYPredicted(); //fetch cluster assignment
            Object goldStandardClass = r.getY(); //the original class of the objervation
            List<Object> tpk = Arrays.<Object>asList(clusterId, goldStandardClass);
            ctMap.put(tpk, ctMap.get(tpk) + 1.0);
            
            //update cluster and class counts
            countOfW.put(clusterId, countOfW.get(clusterId)+1.0);
            countOfC.put(goldStandardClass, countOfC.get(goldStandardClass)+1.0);
        }
        
        double logN = Math.log((double)n);
        double purity=0.0;
        double Iwc=0.0; //http://nlp.stanford.edu/IR-book/html/htmledition/evaluation-of-clustering-1.html
        for(CL cluster : clusterList.values()) {
            Integer clusterId = cluster.getClusterId();
            double maxCounts=Double.NEGATIVE_INFINITY;
            
            //loop through the possible classes and find the most popular one
            for(Object goldStandardClass : modelParameters.getGoldStandardClasses()) {
                List<Object> tpk = Arrays.<Object>asList(clusterId, goldStandardClass);
                double Nwc = ctMap.get(tpk);
                if(Nwc>maxCounts) {
                    maxCounts=Nwc;
                    cluster.setLabelY(goldStandardClass);
                }
                
                if(Nwc>0) {
                    Iwc+= (Nwc/n)*(Math.log(Nwc) -Math.log(countOfC.get(goldStandardClass))
                                   -Math.log(countOfW.get(clusterId)) + logN);
                }
            }
            purity += maxCounts;
        }
        ctMap = null;
        purity/=n;
        
        validationMetrics.setPurity(purity);
        
        double entropyW=0.0;
        for(Double Nw : countOfW.values()) {
            entropyW-=(Nw/n)*(Math.log(Nw)-logN);
        }
        
        double entropyC=0.0;
        for(Double Nc : countOfW.values()) {
            entropyC-=(Nc/n)*(Math.log(Nc)-logN);
        }
        
        validationMetrics.setNMI(Iwc/((entropyW+entropyC)/2.0));
        
        return validationMetrics;
    }
    
    public Map<Integer, CL> getClusters() {
        if(knowledgeBase==null) {
            return null;
        }
        
        return knowledgeBase.getModelParameters().getClusterList();
    }
}