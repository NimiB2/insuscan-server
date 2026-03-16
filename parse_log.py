import re

log_content = """
2026-03-16T12:20:38.853+02:00 DEBUG 40268 --- [insuscan] [nio-9693-exec-2] o.s.web.servlet.DispatcherServlet       : POST "/vision/analyze?email=daniel.s%40gmail.com&portionConfidence=0.05&referenceObjectType=INSULIN_SYRINGE&containerType=REGULAR_BOWL", parameters={multipart}
"""

print(log_content)
