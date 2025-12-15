Text to BPMN - Generation of BPMN Processes from Textual Descriptions
=================================================================================================

This repository provides the training dataset, the textual descriptions used to
evaluate the approach, and the source code of the prototype.
The prototype is also directly available online at <https://lig-givup.imag.fr/>,
provided that you already obtained a password by sending a password request at
[quentin.nivon@inria.fr](mailto:quentin.nivon@inria.fr).

DATASETS directory
=================================================================================================
The DATASETS directory contains two sub-directories: the EXPERIMENTS directory and the
TRAINING directory.

* EXPERIMENTS directory: This directory contains all the natural language descriptions used
to evaluate the approach.
25% of them come from the PET dataset or other proceedings, while 75% of them were handcrafted
by the experts who tested the tool

* TRAINING directory: This directory contains all the training data that was used to fine-tune
GPT.
Each file starting with the letter 'q' is a training file, while each file starting with the
letter 'v' is a validation file.
The files 'train_file.train' and 'valid_file.valid' are aggregated versions of the other files.
Each file is composed of two parts: a description of the process (or user prompt for GPT) visible
above the "ASSISTANT:" tag, and a set of expressions (or assistant prompt for GPT) corresponding
to the expected output of GPT for the given description (visible below the "ASSISTANT:" tag). 

TOOL directory
=================================================================================================
This directory contains the source code of the prototype used in this approach.
It is worth noting that one CAN NOT make usage of this source code as it requires the authors'
personal OpenAI API key to work.
However, anyone interested in can duplicate this source code, fine-tune his own model and make
usage of this source code.

Contributors
=================================================================================================

* [Quentin Nivon](https://quentinnivon.github.io/)
* [Gwen Salaün](http://convecs.inria.fr/people/Gwen.Salaun/) 

License
=================================================================================================
This source code and all the related contents are distributed under the CeCILL license (see
LICENSE.txt for more information).
They are distributed as is and without any guarantee of any kind in terms of (re)usability,
usage, availability, sustainability, validity, or results.

