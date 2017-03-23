package csw.services.location.models

/**
  * Used to identify a component
  *
  * @param name          the service name
  * @param componentType HCD, Assembly, Service
  */
case class ComponentId(name: String, componentType: ComponentType) extends TmtSerializable {
  require(name == name.trim, "component name has leading and trailing whitespaces")

  //'-' in the name leads to confusing connection strings in the UI listing services
  require(!name.contains("-"), "component name has '-'")
}
