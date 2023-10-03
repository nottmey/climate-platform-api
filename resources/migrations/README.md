
## Disclaimers

### `:db.type/ref` attributes

Use a new `:db.type/ref` attribute for every link between two types, else
you will get collisions, where you don't know whether a link target is of a
certain graphql type. This would especially affect back references, where we 
would simply get all attribute usages, regardless of entity type.

You would need to implement filtering based on the type, which we currently 
don't do.

e.g., name a ref `:breakdown/data-points` instead of `:platform/data-points`, 
because you should not reuse the attribute anywhere else, other than linking a 
"breakdown" with (in this case) multiple "data points"
