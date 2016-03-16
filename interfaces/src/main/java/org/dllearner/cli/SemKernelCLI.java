package org.dllearner.cli;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.dllearner.configuration.IConfiguration;
import org.dllearner.configuration.spring.ApplicationContextBuilder;
import org.dllearner.configuration.spring.DefaultApplicationContextBuilder;
import org.dllearner.confparser.ConfParserConfiguration;
import org.dllearner.core.ComponentInitException;
import org.dllearner.utilities.semkernel.SemKernelWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

/**
 * SemKernel command line interface
 *
 * @author Patrick Westphal
 */
public class SemKernelCLI {
    private static Logger logger = LoggerFactory.getLogger(SemKernelCLI.class);

    private ApplicationContext context;
    private File confFile;
    private IConfiguration configuration;

    private SemKernelWorkflow semkernelWorkflow;

    public SemKernelCLI() {
    }

    public SemKernelCLI(File confFile) {
        this();
        this.setConfFile(confFile);
    }

    public static void main(String[] args) throws FileNotFoundException {
        System.out.println("SemKernel command line interface");

        if(args.length == 0) {
            System.out.println("You need to give a conf file as argument.");
            System.exit(0);
        }

        // read file and print a message if it does not exist
        File file = new File(args[args.length - 1]);
        if(!file.exists()) {
            System.out.println("File \"" + file + "\" does not exist.");
            System.exit(0);
        }

        Resource confFile = new FileSystemResource(file);

        List<Resource> springConfigResources = new ArrayList<>();

        try {
            //SemKernel configuration object
            IConfiguration configuration = new ConfParserConfiguration(confFile);

            ApplicationContextBuilder builder =
                    new DefaultApplicationContextBuilder();
            ApplicationContext context = builder.buildApplicationContext(
                    configuration, springConfigResources);

            SemKernelCLI cli = new SemKernelCLI();

            cli.setContext(context);
            cli.setConfFile(file);
            cli.run();

        } catch (Exception e) {e.printStackTrace();
            String stacktraceFileName = "log/error.log";

            //Find the primary cause of the exception.
            Throwable primaryCause = findPrimaryCause(e);

            // Get the Root Error Message
            logger.error("An Error Has Occurred During Processing.");
            logger.debug("Stack Trace: ", e);
            logger.error("Terminating DL-Learner...and writing stacktrace " +
                    "to: " + stacktraceFileName);
            FileOutputStream fos = new FileOutputStream(stacktraceFileName);
            PrintStream ps = new PrintStream(fos);
            e.printStackTrace(ps);
        }
    }

    /**
     * Find the primary cause of the specified exception.
     *
     * @param e The exception to analyze
     * @return The primary cause of the exception.
     */
    private static Throwable findPrimaryCause(Exception e) {
        // The throwables from the stack of the exception
        Throwable[] throwables = ExceptionUtils.getThrowables(e);

        // Look For a Component Init Exception and use that as the primary
        // cause of failure, if we find it
        int componentInitExceptionIndex = ExceptionUtils.indexOfThrowable(e,
                ComponentInitException.class);

        Throwable primaryCause;
        if(componentInitExceptionIndex > -1) {
            primaryCause = throwables[componentInitExceptionIndex];
        }else {
            // No Component Init Exception on the Stack Trace, so we'll use the
            // root as the primary cause.
            primaryCause = ExceptionUtils.getRootCause(e);
        }
        return primaryCause;
    }

    public void init() throws IOException {
        if(getContext() == null) {
            Resource confFileR = new FileSystemResource(getConfFile());
            List<Resource> springConfigResources = new ArrayList<>();
            configuration = new ConfParserConfiguration(confFileR);

            ApplicationContextBuilder builder = new DefaultApplicationContextBuilder();
            setContext(builder.buildApplicationContext(
                    configuration, springConfigResources));

//            knowledgeSource = context.getBean(KnowledgeSource.class);
//            rs = getMainReasonerComponent();
//            la = context.getBean(AbstractCELA.class);
//            lp = context.getBean(AbstractLearningProblem.class);
        }
    }

    public void run() {
        for(Entry<String, SemKernelWorkflow> entry : getContext().
                getBeansOfType(SemKernelWorkflow.class).entrySet()){

            semkernelWorkflow = entry.getValue();
            logger.info("Running SemKernel workflow \"" + entry.getKey() + "\" (" + semkernelWorkflow.getClass().getSimpleName() + ")");
            semkernelWorkflow.start();
        }
    }

    public ApplicationContext getContext() {
        return context;
    }

    public void setContext(ApplicationContext context) {
        this.context = context;
    }

    public File getConfFile() {
        return confFile;
    }

    public void setConfFile(File confFile) {
        this.confFile = confFile;
    }
}
