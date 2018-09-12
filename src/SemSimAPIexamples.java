
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.zip.ZipFile;

import javax.xml.stream.XMLStreamException;

import org.jdom.JDOMException;
import org.semanticweb.owlapi.model.OWLException;

import semsim.SemSimLibrary;
import semsim.annotation.ReferenceOntologyAnnotation;
import semsim.annotation.ReferenceTerm;
import semsim.definitions.SemSimRelations.SemSimRelation;
import semsim.definitions.SemSimTypes;
import semsim.fileaccessors.FileAccessorFactory;
import semsim.fileaccessors.ModelAccessor;
import semsim.model.collection.SemSimModel;
import semsim.model.computational.datastructures.DataStructure;
import semsim.model.physical.PhysicalEntity;
import semsim.model.physical.PhysicalModelComponent;
import semsim.model.physical.PhysicalProcess;
import semsim.model.physical.object.CompositePhysicalEntity;
import semsim.reading.CellMLreader;
import semsim.reading.ModelClassifier.ModelType;
import semsim.reading.OMEXmanifestReader;
import semsim.reading.SBMLreader;
import semsim.reading.SemSimOWLreader;


/**
 * Some examples of how to work with SemSim models using the SemSim API.
 * @author mneal
 *
 */
public class SemSimAPIexamples {

	
	public static void main(String[] args) throws IOException, InterruptedException, XMLStreamException, OWLException, JDOMException {
		
		SemSimLibrary sslib = new SemSimLibrary();
		
		// Read in an SBML model
		File modelfile = new File("./test/BIOMD0000000355.xml");
		ModelAccessor ma = FileAccessorFactory.getModelAccessor(modelfile);
		SemSimModel semsimmodel = new SBMLreader(ma).read();
		System.out.println("Loaded " + semsimmodel.getName());
		
		
		
		// Read in a SemSim model
		modelfile = new File("./test/BIOMD0000000176.owl");
		ma = FileAccessorFactory.getModelAccessor(modelfile);
		semsimmodel = new SemSimOWLreader(ma).read();
		System.out.println("Loaded " + semsimmodel.getName());
		
		System.out.println("\n\nHere are the model's processes and the entities that participate in them:");
		// Write out all physical processes in the model, their participants, and the participants' stoichiometries
		// Note that ReferencePhysicalProcesses are only included in a model to annotate CustomPhysicalProcesses (isVersionOf, etc.)
		// so we just want to list the CustomPhysicalProcesses here
		for(PhysicalProcess process : semsimmodel.getCustomPhysicalProcesses()){ 
			System.out.println("\nProcess: " + process.getName());
			
			for(PhysicalEntity src : process.getSources().keySet())
				System.out.println(" Source: " + src.getName() + " x" + process.getSources().get(src));
			
			for(PhysicalEntity snk : process.getSinks().keySet())
				System.out.println(" Sink: " + snk.getName() + " x" + process.getSinks().get(snk));
			
			for(PhysicalEntity med : process.getMediators())
				System.out.println(" Mediator: " + med.getName());
			
		}
		
		
		
		// Load an annotated CellML model within an OMEX archive
		File omexfile = new File("./test/smith_chase_nokes_shaw_wake_2004.omex");
		ArrayList<ModelAccessor> mas = OMEXmanifestReader.getModelsInArchive(new ZipFile(omexfile), omexfile);
		ModelAccessor omexma = mas.get(0);
		
		if(omexma.getModelType()==ModelType.CELLML_MODEL) 
			semsimmodel = new CellMLreader(omexma).read();
		else{
			System.err.println("Model in archive was " + omexma.getModelType() + " but was expecting a CellML model");
			return;
		}

		
		// Write out descriptions on all the data structures in the model.
		// For data structures with composite annotations that use a physical property 
		// and a composite physical entity, write out URIs of the components in the annotation.
		System.out.println("\n\nAn example of a composite annotation:\n");
		for(DataStructure ds : semsimmodel.getAssociatedDataStructures()){
						
			// If the data structure doesn't have an associated physical property
			// or doesn't have an associated physical component, ignore it
			// since we only want to write out those with full composite annotations.
			if( ! ds.hasPhysicalProperty() || ! ds.hasAssociatedPhysicalComponent()) continue;

			System.out.println("\n" + ds.getName() + " (" + ds.getDescription() + ")");
			System.out.println(" <has physical property>\n" + ds.getPhysicalProperty().getPhysicalDefinitionURI());
			PhysicalModelComponent pmc = ds.getAssociatedPhysicalModelComponent();
			System.out.println(" <property of>");

			// Pick a composite annotation that uses a composite physical entity
			if( ! pmc.isType(SemSimTypes.COMPOSITE_PHYSICAL_ENTITY))
				continue;
				
			CompositePhysicalEntity cpe = (CompositePhysicalEntity)pmc;
			
			// Iterate through the components of the composite physical entity
			int numpe = cpe.getArrayListOfEntities().size();
			
			for(int i=0; i<numpe; i++){
				PhysicalEntity ent = cpe.getArrayListOfEntities().get(i);
				String entstring = ent instanceof ReferenceTerm ? 
						((ReferenceTerm)ent).getPhysicalDefinitionURI().toString() : ent.getDescription();
				System.out.println(entstring);
				
				// Output structural relations between components of the composite entity
				if(i<numpe-1){
					URI structreluri = cpe.getArrayListOfStructuralRelations().get(i).getURI();
					System.out.println(" <" + structreluri + ">");
				}
			}
		}
		
		// Add a singular (non-composite) annotation to a data structure that provides its complete physical definition
		System.out.println("\n\nAn example of a singular annotation:");
		DataStructure ds = semsimmodel.getAssociatedDataStructure("environment.time");
		URI uri = URI.create("http://identifiers.org/opb/OPB_01023");
		ds.addReferenceOntologyAnnotation(SemSimRelation.BQB_IS, uri, "Time", sslib);
		
		// Iterate through all reference ontology annotations on the data structure and output them to console.
		for(ReferenceOntologyAnnotation roa : ds.getAllReferenceOntologyAnnotations())
			System.out.println("\n" + ds.getName() + " <" + roa.getRelation().getURI() + "> " + roa.getReferenceURI());

		
		// Write out a model as a SemSim OWL file
		File outfile = new File("./test/output/semsimowltest.owl");
		ModelAccessor outacc = FileAccessorFactory.getModelAccessor(outfile, ModelType.SEMSIM_MODEL);
		outacc.writetoFile(semsimmodel);
		
		// Write out a model as a standalone CellML file
		outfile = new File("./test/output/cellmltest.owl");
		outacc = FileAccessorFactory.getModelAccessor(outfile, ModelType.CELLML_MODEL);
		outacc.writetoFile(semsimmodel);
		
	}
}
