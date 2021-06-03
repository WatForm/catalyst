module decider
open Declaration
//open Wordpress
//open DBLP

one sig NameSpace extends Class{}{
attrSet = namespaceID+namespaceName
id=namespaceID
isAbstract = No
no parent
}

one sig namespaceID extends Integer{}
one sig namespaceName extends string{}


one sig Variable extends Class{}{
attrSet = variableID+variableName
id=variableID
isAbstract = No
no parent
}

one sig variableID extends Integer{}
one sig variableName extends string{}

one sig Relationship extends Class{}{
attrSet = relationshipID+relationshipName
id=relationshipID
isAbstract = No
no parent
}

one sig relationshipID extends Integer{}
one sig relationshipName extends string{}



one sig varAssociation extends Association{}{
src = Variable
dst = Relationship
src_multiplicity = ONE
dst_multiplicity = MANY
}


one sig Role extends Class{}{
attrSet = roleID //+roleName
id=roleID
isAbstract = No
no parent
}

one sig roleID extends Integer{}
//one sig roleName extends string{}


one sig DecisionSpace extends Class{}{
attrSet = decisionSpaceName
id=namespaceID
isAbstract = No
one parent
parent in NameSpace
}

one sig decisionSpaceName extends string{}



one sig RoleBindingsAssociation extends Association{}{
src = Participant
dst = Role
src_multiplicity = MANY
dst_multiplicity = MANY
}

one sig Participant extends Class{}{
attrSet=participantID
id=participantID
isAbstract = No
no parent
}
one sig participantID extends Integer{}


one sig descisionSpaceParticipantsAssociation extends Association{}{
src = DecisionSpace
dst = Participant
src_multiplicity = MANY
dst_multiplicity = MANY
}


one sig descisionSpaceVariablesAssociation extends Association{}{
src = DecisionSpace
dst = Variable
src_multiplicity = ONE
dst_multiplicity = MANY
}


one sig User extends Class{}{
attrSet = username+password
id=participantID
one parent
parent in Participant
isAbstract = No
}

one sig username extends string{}
one sig password extends string{}

one sig Viewer extends Class{}{
attrSet = period
id=participantID
one parent
parent in Participant
isAbstract = No
}

one sig period extends Integer{}


one sig Developer extends Class{}{
attrSet = department
id=participantID
one parent
parent in User
isAbstract = No
}

one sig department extends Integer{}

/*
one sig DSN extends Class{}{
attrSet = DSNID
id=namespaceID
isAbstract = No
one parent
parent in DecisionSpace
}

one sig DSNID extends Integer{}
*/

pred Decider{}
run Decider for 30
