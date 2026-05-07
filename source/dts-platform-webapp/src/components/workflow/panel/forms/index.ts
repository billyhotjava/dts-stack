import type { ComponentType } from "react";
import type { WorkflowNodeKind } from "../../store/types";
import { EndForm } from "./EndForm";
import { IterationForm } from "./IterationForm";
import { LoopForm } from "./LoopForm";
import { NoteForm } from "./NoteForm";
import { SinkForm } from "./SinkForm";
import { SourceForm } from "./SourceForm";
import { StartForm } from "./StartForm";
import { TransformForm } from "./TransformForm";
import type { NodeFormProps } from "./types";
import { ValidateForm } from "./ValidateForm";

export const NODE_FORMS: Partial<Record<WorkflowNodeKind, ComponentType<NodeFormProps>>> = {
	start: StartForm,
	source: SourceForm,
	transform: TransformForm,
	validate: ValidateForm,
	sink: SinkForm,
	end: EndForm,
	iteration: IterationForm,
	loop: LoopForm,
	note: NoteForm,
};
